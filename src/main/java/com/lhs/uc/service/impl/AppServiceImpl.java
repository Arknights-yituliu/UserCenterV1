package com.lhs.uc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhs.uc.common.enums.ResultCode;
import com.lhs.uc.common.exception.BusinessException;
import com.lhs.uc.common.util.RedisKeyUtil;
import com.lhs.uc.entity.dto.SessionInfo;
import com.lhs.uc.entity.po.UserInfo;
import com.lhs.uc.entity.po.UserProfile;
import com.lhs.uc.entity.vo.app.AppProfileVO;
import com.lhs.uc.entity.vo.app.AppUserVO;
import com.lhs.uc.mapper.UserInfoMapper;
import com.lhs.uc.mapper.UserProfileMapper;
import com.lhs.uc.service.AppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * 接入方服务实现：Token 校验、用户信息拉取、踢用户下线
 *
 * @author UserCenter
 */
@Service
public class AppServiceImpl implements AppService {

    private static final Logger log = LoggerFactory.getLogger(AppServiceImpl.class);

    /** 会话 key 扫描模式 */
    private static final String TOKEN_PATTERN = "uc:token:*";

    private final UserInfoMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param userMapper          用户 Mapper
     * @param profileMapper       站点资料 Mapper
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public AppServiceImpl(UserInfoMapper userMapper, UserProfileMapper profileMapper,
                          StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验用户 token：实时查询 Redis 会话，返回用户全局资料 + 站点扩展资料
     *
     * @param token 用户会话 token
     * @param appId 来源应用 AppId
     * @return 用户信息
     */
    @Override
    public AppUserVO verifyToken(String token, String appId) {
        String sessionJson = stringRedisTemplate.opsForValue().get(RedisKeyUtil.token(token));
        if (sessionJson == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        try {
            SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
            if (session == null || session.getUid() == null) {
                throw new BusinessException(ResultCode.NOT_LOGIN);
            }
            return buildAppUserVO(session.getUid(), appId);
        } catch (IOException e) {
            log.error("会话 JSON 解析失败", e);
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
    }

    /**
     * 按 uid 拉取用户全局资料 + 指定站点扩展资料
     *
     * @param uid   用户 uid
     * @param appId 来源应用 AppId
     * @return 用户信息
     */
    @Override
    public AppUserVO getUserInfo(Long uid, String appId) {
        return buildAppUserVO(uid, appId);
    }

    /**
     * 踢指定用户全部设备下线（删除该用户所有会话）
     *
     * @param uid 用户 uid
     */
    @Override
    public void kickUser(Long uid) {
        Set<String> tokenKeys = scanKeys(TOKEN_PATTERN);
        int kicked = 0;
        for (String key : tokenKeys) {
            String sessionJson = stringRedisTemplate.opsForValue().get(key);
            if (sessionJson == null) {
                continue;
            }
            try {
                SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
                if (session != null && uid.equals(session.getUid())) {
                    stringRedisTemplate.delete(key);
                    kicked++;
                }
            } catch (IOException e) {
                log.warn("会话解析失败，跳过 key：{}", key);
            }
        }
        log.info("已踢下线用户 {} 的 {} 个会话", uid, kicked);
    }

    /**
     * 组装用户信息 VO（全局资料 + 站点资料）
     *
     * @param uid   用户 uid
     * @param appId 来源应用 AppId
     * @return 用户信息
     */
    private AppUserVO buildAppUserVO(Long uid, String appId) {
        UserInfo user = userMapper.selectOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUid, uid));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        AppUserVO vo = new AppUserVO();
        vo.setUid(user.getUid());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());

        // 站点扩展资料（不存在则返回 null）
        UserProfile profile = profileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUid, uid)
                .eq(UserProfile::getAppId, appId));
        if (profile != null) {
            AppProfileVO profileVO = new AppProfileVO();
            profileVO.setNickname(profile.getNickname());
            profileVO.setAvatar(profile.getAvatar());
            profileVO.setExt(profile.getExt());
            vo.setProfile(profileVO);
        }
        return vo;
    }

    /**
     * 按模式扫描 Redis key（使用 SCAN，避免阻塞 Redis）
     *
     * @param pattern 匹配模式
     * @return key 集合
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
