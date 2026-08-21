package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.DesensitizeUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.SessionInfo;
import com.orange.entity.dto.user.BindEmailRequest;
import com.orange.entity.dto.user.ChangeEmailRequest;
import com.orange.entity.dto.user.UpdatePasswordRequest;
import com.orange.entity.dto.user.UpdateProfileRequest;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.SessionVO;
import com.orange.entity.vo.UserInfoVO;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.EmailCodeService;
import com.orange.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务实现：个人资料、修改密码、会话管理
 *
 * @author UserCenter
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 会话 key 扫描模式 */
    private static final String TOKEN_PATTERN = "uc:token:*";

    private final UserInfoMapper userMapper;
    private final EmailCodeService emailCodeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造器注入依赖
     *
     * @param userMapper          用户 Mapper
     * @param emailCodeService    验证码服务（绑定/换绑邮箱）
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public UserServiceImpl(UserInfoMapper userMapper, EmailCodeService emailCodeService,
                           StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.emailCodeService = emailCodeService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 获取当前用户资料
     *
     * @param uid 用户 uid
     * @return 用户信息
     */
    @Override
    public UserInfoVO getProfile(Long uid) {
        UserInfo user = getByUid(uid);
        return toUserInfoVO(user);
    }

    /**
     * 修改个人资料（昵称/头像）
     *
     * @param uid     用户 uid
     * @param request 修改参数
     */
    @Override
    public void updateProfile(Long uid, UpdateProfileRequest request) {
        UserInfo user = getByUid(uid);
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        userMapper.updateById(user);
    }

    /**
     * 修改密码（校验旧密码）
     *
     * @param uid     用户 uid
     * @param request 修改参数
     */
    @Override
    public void updatePassword(Long uid, UpdatePasswordRequest request) {
        UserInfo user = getByUid(uid);
        if (user.getPassword() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该账号未设置密码，请使用验证码方式修改");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 安全考虑：修改密码后踢出该用户所有其他会话（当前会话由前端重新登录）
        kickAllSessions(uid);
    }

    /**
     * 绑定邮箱（面向无邮箱用户：校验新邮箱未被占用 + 验证码所有权后写入）
     *
     * @param uid     用户 uid
     * @param request 绑定参数（新邮箱 + 验证码）
     */
    @Override
    public void bindEmail(Long uid, BindEmailRequest request) {
        UserInfo user = getByUid(uid);
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.ILLEGAL_OPERATION, "该账号已绑定邮箱，如需更换请使用换绑功能");
        }
        checkEmailAvailable(request.getEmail());
        emailCodeService.verifyCode(request.getEmail(), request.getCode());
        user.setEmail(request.getEmail());
        userMapper.updateById(user);
    }

    /**
     * 发送换绑邮箱验证码：直接发到当前账号绑定的邮箱
     *
     * <p>前端只持有脱敏邮箱，无法走公开发码接口，故提供登录态发码入口，
     * 由服务端从会话取当前邮箱发送，全程不暴露完整邮箱</p>
     *
     * @param uid 用户 uid
     * @param ip  请求 IP（发送限流维度）
     */
    @Override
    public void sendChangeEmailCode(Long uid, String ip) {
        UserInfo user = getByUid(uid);
        String currentEmail = user.getEmail();
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该账号未绑定邮箱，请先绑定邮箱");
        }
        emailCodeService.sendCode(currentEmail, "change_email", ip);
    }

    /**
     * 换绑邮箱（需同时验证旧邮箱与新邮箱的所有权）
     *
     * @param uid     用户 uid
     * @param request 换绑参数（旧邮箱验证码 + 新邮箱 + 新邮箱验证码）
     */
    @Override
    public void changeEmail(Long uid, ChangeEmailRequest request) {
        UserInfo user = getByUid(uid);
        // 旧邮箱以服务端当前绑定为准（前端只回传脱敏值无法比对），仅校验旧邮箱验证码
        String currentEmail = user.getEmail();
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该账号未绑定邮箱，请先绑定邮箱");
        }
        if (currentEmail.equals(request.getNewEmail())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新邮箱与当前邮箱相同");
        }
        // 先验证旧邮箱所有权，再校验新邮箱未被占用，最后验证新邮箱所有权
        emailCodeService.verifyCode(currentEmail, request.getOldCode());
        checkEmailAvailable(request.getNewEmail());
        emailCodeService.verifyCode(request.getNewEmail(), request.getNewCode());
        user.setEmail(request.getNewEmail());
        userMapper.updateById(user);
    }

    /**
     * 校验邮箱未被其他用户占用（uk_email 唯一约束的业务前置检查）
     *
     * @param email 目标邮箱
     */
    private void checkEmailAvailable(String email) {
        if (userMapper.selectCount(Wrappers.<UserInfo>lambdaQuery()
                .eq(UserInfo::getEmail, email)) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }
    }

    /**
     * 查看当前用户的全部登录设备
     *
     * @param uid 用户 uid
     * @return 会话列表
     */
    @Override
    public List<SessionVO> listSessions(Long uid) {
        List<SessionVO> sessions = new ArrayList<>();
        for (String key : scanKeys(TOKEN_PATTERN)) {
            String sessionJson = stringRedisTemplate.opsForValue().get(key);
            if (sessionJson == null) {
                continue;
            }
            try {
                SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
                if (session != null && uid.equals(session.getUid())) {
                    SessionVO vo = new SessionVO();
                    vo.setToken(key.substring(RedisKeyUtil.token("").length()));
                    vo.setAppId(session.getAppId());
                    vo.setLoginTime(session.getCreateTime());
                    sessions.add(vo);
                }
            } catch (IOException e) {
                log.warn("会话解析失败，跳过 key：{}", key);
            }
        }
        return sessions;
    }

    /**
     * 踢指定设备下线（校验会话归属当前用户）
     *
     * @param uid   用户 uid
     * @param token 要踢出的会话 token
     */
    @Override
    public void kickSession(Long uid, String token) {
        String sessionJson = stringRedisTemplate.opsForValue().get(RedisKeyUtil.token(token));
        if (sessionJson == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该会话不存在或已失效");
        }
        try {
            SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
            if (session == null || !uid.equals(session.getUid())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该会话");
            }
            stringRedisTemplate.delete(RedisKeyUtil.token(token));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "会话解析失败");
        }
    }

    /**
     * 按 uid 查询用户，不存在则抛异常
     *
     * @param uid 用户 uid
     * @return 用户实体
     */
    private UserInfo getByUid(Long uid) {
        UserInfo user = userMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUid, uid));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 踢出用户全部会话
     *
     * @param uid 用户 uid
     */
    @Override
    public void kickAllSessions(Long uid) {
        for (String key : scanKeys(TOKEN_PATTERN)) {
            String sessionJson = stringRedisTemplate.opsForValue().get(key);
            if (sessionJson == null) {
                continue;
            }
            try {
                SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
                if (session != null && uid.equals(session.getUid())) {
                    stringRedisTemplate.delete(key);
                }
            } catch (IOException e) {
                log.warn("会话解析失败，跳过 key：{}", key);
            }
        }
    }

    /**
     * 按模式扫描 Redis key（使用 SCAN，避免阻塞 Redis）
     *
     * @param pattern 匹配模式
     * @return key 集合
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    /**
     * 用户实体转视图对象
     *
     * @param user 用户实体
     * @return 用户信息视图
     */
    private UserInfoVO toUserInfoVO(UserInfo user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUid(user.getUid());
        vo.setEmail(DesensitizeUtil.maskEmail(user.getEmail()));
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setRegisterTime(user.getRegisterTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        return vo;
    }
}
