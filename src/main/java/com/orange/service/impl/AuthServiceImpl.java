package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.SessionInfo;
import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.po.LoginLog;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.auth.LoginVO;
import com.orange.mapper.LoginLogMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.AuthService;
import com.orange.service.EmailCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现：注册、登录（密码/邮箱验证码）、登出、会话签发
 *
 * @author UserCenter
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** 登录方式常量：密码 */
    private static final String TYPE_PASSWORD = "password";

    /** 登录方式常量：邮箱验证码 */
    private static final String TYPE_EMAIL_CODE = "email";

    private final UserInfoMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final EmailCodeService emailCodeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    /** 会话有效期（秒） */
    @Value("${uc.session-ttl-seconds:2592000}")
    private long sessionTtlSeconds;

    /** 登录失败次数上限 */
    @Value("${uc.login-fail-limit:5}")
    private long loginFailLimit;

    /** 登录锁定时间（秒） */
    @Value("${uc.login-lock-seconds:900}")
    private long loginLockSeconds;

    /**
     * 构造器注入依赖
     *
     * @param userMapper          用户 Mapper
     * @param loginLogMapper      登录日志 Mapper
     * @param emailCodeService    验证码服务
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public AuthServiceImpl(UserInfoMapper userMapper, LoginLogMapper loginLogMapper,
                           EmailCodeService emailCodeService, StringRedisTemplate stringRedisTemplate,
                           ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.emailCodeService = emailCodeService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 注册（密码注册 / 邮箱验证码注册），注册成功后直接签发会话登录
     *
     * @param request 注册参数
     * @param ip      注册 IP
     * @param appId   来源应用 AppId
     * @return 登录响应（含 token）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterRequest request, String ip, String appId) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱不能为空");
        }
        // 校验邮箱唯一
        if (userMapper.selectCount(Wrappers.<UserInfo>lambdaQuery()
                .eq(UserInfo::getEmail, request.getEmail())) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        UserInfo user = new UserInfo();
        user.setUid(IdWorker.getId());
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? request.getEmail() : request.getNickname());
        user.setIp(ip);
        user.setStatus(1);
        user.setRegisterTime(LocalDateTime.now());

        if ("password".equals(request.getRegisterType())) {
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "密码不能为空");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        } else if ("email_code".equals(request.getRegisterType())) {
            // 校验邮箱验证码
            emailCodeService.verifyCode(request.getEmail(), request.getVerificationCode());
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的注册方式");
        }

        userMapper.insert(user);
        return buildLoginVO(user, createSession(user.getUid(), appId));
    }

    /**
     * 登录（密码 / 邮箱验证码），并记录登录日志
     *
     * @param request 登录参数
     * @param ip      登录 IP
     * @param ua      浏览器 UA
     * @param appId   来源应用 AppId
     * @return 登录响应（含 token）
     */
    @Override
    public LoginVO login(LoginRequest request, String ip, String ua, String appId) {
        UserInfo user;
        String loginType;

        if (TYPE_PASSWORD.equals(request.getAccountType())) {
            user = passwordLogin(request.getEmail(), request.getPassword());
            loginType = TYPE_PASSWORD;
        } else if (TYPE_EMAIL_CODE.equals(request.getAccountType())) {
            user = emailCodeLogin(request.getEmail(), request.getVerificationCode());
            loginType = TYPE_EMAIL_CODE;
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的登录方式");
        }

        // 校验账号状态
        checkUserStatus(user);
        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        String token = createSession(user.getUid(), appId);
        writeLoginLog(user.getUid(), appId, loginType, ip, ua, 1);
        return buildLoginVO(user, token);
    }

    /**
     * 登出：删除 Redis 会话
     *
     * @param token 会话 token
     */
    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            stringRedisTemplate.delete(RedisKeyUtil.token(token));
        }
    }

    /**
     * 签发会话：生成 token 并写入 Redis（设备数不限，删除 key 即踢下线）
     *
     * @param uid   用户 uid
     * @param appId 来源应用 AppId（可为空）
     * @return 会话 token
     */
    @Override
    public String createSession(Long uid, String appId) {
        String token = SignUtil.generateToken();
        SessionInfo session = new SessionInfo(uid, appId == null ? "" : appId, LocalDateTime.now());
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyUtil.token(token),
                    objectMapper.writeValueAsString(session),
                    sessionTtlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "会话创建失败");
        }
        return token;
    }

    /**
     * 密码登录：校验登录锁定、BCrypt 密码、失败计数
     *
     * @param email    邮箱
     * @param password 明文密码
     * @return 用户实体
     */
    private UserInfo passwordLogin(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱和密码不能为空");
        }
        UserInfo user = userMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getEmail, email));
        if (user == null) {
            // 用户不存在也累计失败次数，防止撞库探测
            recordLoginFail(email);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 登录锁定校验
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeyUtil.loginLock(email)))) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordLoginFail(email);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        clearLoginFail(email);
        return user;
    }

    /**
     * 邮箱验证码登录：校验验证码，未注册邮箱自动注册
     *
     * @param email 邮箱
     * @param code  验证码
     * @return 用户实体
     */
    private UserInfo emailCodeLogin(String email, String code) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱不能为空");
        }
        emailCodeService.verifyCode(email, code);

        UserInfo user = userMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getEmail, email));
        if (user == null) {
            // 未注册则自动注册
            user = new UserInfo();
            user.setUid(IdWorker.getId());
            user.setEmail(email);
            user.setNickname(email);
            user.setStatus(1);
            user.setRegisterTime(LocalDateTime.now());
            userMapper.insert(user);
        }
        return user;
    }

    /**
     * 校验账号状态：封禁账号拒绝登录
     *
     * @param user 用户实体
     */
    private void checkUserStatus(UserInfo user) {
        if (user.getStatus() != null && user.getStatus() < 0) {
            throw new BusinessException(ResultCode.USER_BANNED);
        }
    }

    /**
     * 记录一次登录失败并累计，达到阈值后锁定账号
     *
     * @param email 登录账号（邮箱）
     */
    private void recordLoginFail(String email) {
        String failKey = RedisKeyUtil.loginFail(email);
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failKey, loginLockSeconds, TimeUnit.SECONDS);
        }
        if (count != null && count >= loginFailLimit) {
            // 达到阈值：锁定账号
            stringRedisTemplate.opsForValue().set(
                    RedisKeyUtil.loginLock(email), "1", loginLockSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.delete(failKey);
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }
    }

    /**
     * 登录成功后清除失败计数与锁定标记
     *
     * @param email 登录账号（邮箱）
     */
    private void clearLoginFail(String email) {
        stringRedisTemplate.delete(RedisKeyUtil.loginFail(email));
        stringRedisTemplate.delete(RedisKeyUtil.loginLock(email));
    }

    /**
     * 写登录日志
     *
     * @param uid       用户 uid
     * @param appId     来源应用
     * @param loginType 登录方式
     * @param ip        登录 IP
     * @param ua        UA
     * @param status    1=成功 0=失败
     */
    private void writeLoginLog(Long uid, String appId, String loginType, String ip, String ua, int status) {
        LoginLog log = new LoginLog();
        log.setUid(uid);
        log.setAppId(appId);
        log.setLoginType(loginType);
        log.setIp(ip);
        log.setUserAgent(ua);
        log.setStatus(status);
        log.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    /**
     * 构建登录响应
     *
     * @param user  用户实体
     * @param token 会话 token
     * @return 登录响应
     */
    private LoginVO buildLoginVO(UserInfo user, String token) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUid(user.getUid());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        return vo;
    }
}
