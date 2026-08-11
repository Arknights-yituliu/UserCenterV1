package com.orange.interceptor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.SignUtil;
import com.orange.entity.po.AppInfo;
import com.orange.mapper.AppInfoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 接入方签名校验拦截器
 *
 * <p>校验 /api/app/** 请求的 AppId/Timestamp/Nonce/Sign 头，防重放、防篡改。</p>
 *
 * <p>校验顺序：AppId 存在且启用 → 时间戳窗口（±5 分钟）→ 签名比对 → Nonce 唯一性。
 * 签名比对放在 Nonce 写入之前，避免攻击者构造非法请求占用 Redis 存储。</p>
 *
 * @author UserCenter
 */
@Component
public class AppSignInterceptor implements HandlerInterceptor {

    private final AppInfoMapper appMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 签名窗口（秒），与 application.yml 中 uc.sign-window-seconds 对应 */
    @Value("${user-center.sign-window-seconds:300}")
    private long signWindowSeconds;

    /**
     * 构造器注入依赖
     *
     * @param appMapper            应用 Mapper
     * @param stringRedisTemplate  Redis 客户端
     */
    public AppSignInterceptor(AppInfoMapper appMapper, StringRedisTemplate stringRedisTemplate) {
        this.appMapper = appMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求处理前执行签名校验
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return 是否放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 提取签名头
        String appId = request.getHeader("AppId");
        String timestamp = request.getHeader("Timestamp");
        String nonce = request.getHeader("Nonce");
        String sign = request.getHeader("Sign");

        if (appId == null || timestamp == null || nonce == null || sign == null) {
            throw new BusinessException(ResultCode.SIGN_ERROR, "缺少签名请求头（AppId/Timestamp/Nonce/Sign）");
        }

        // 2. 校验 AppId 存在且启用
        AppInfo app = appMapper.selectOne(
                Wrappers.<AppInfo>lambdaQuery().eq(AppInfo::getAppId, appId));
        if (app == null) {
            throw new BusinessException(ResultCode.APP_NOT_FOUND);
        }
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new BusinessException(ResultCode.APP_DISABLED);
        }

        // 3. 校验时间戳窗口（防旧请求重放）
        long now = System.currentTimeMillis();
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.SIGN_ERROR, "时间戳格式错误");
        }
        if (Math.abs(now - requestTime) > signWindowSeconds * 1000) {
            throw new BusinessException(ResultCode.SIGN_TIMESTAMP_EXPIRED);
        }

        // 4. 计算签名并比对（防参数篡改）
        String body = readBody(request);
        String expectSign = SignUtil.hmacSha256(app.getAppSecret(), appId, timestamp, nonce, body);
        if (!expectSign.equalsIgnoreCase(sign)) {
            throw new BusinessException(ResultCode.SIGN_ERROR);
        }

        // 5. 校验 Nonce 唯一性（防时间窗内重复请求），签名合法后才写入 Redis
        String nonceKey = RedisKeyUtil.nonce(nonce);
        // Nonce 存储 TTL 与签名窗口一致，防止窗口内重放
        Boolean firstUse = stringRedisTemplate.opsForValue().setIfAbsent(nonceKey, "1", signWindowSeconds, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstUse)) {
            throw new BusinessException(ResultCode.REPLAY_ATTACK);
        }

        // 6. 将当前应用 AppId 存入请求属性，供下游 Controller 获取
        request.setAttribute("appId", appId);
        return true;
    }

    /**
     * 读取请求体规范串（签名计算用）
     *
     * <p>请求体已由 ContentCachingFilter 包装缓存，此处读取不会影响 Controller 再次读取</p>
     *
     * @param request 请求
     * @return 请求体字符串（无 body 时为空串）
     */
    private String readBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            try {
                if (wrapper.getContentAsByteArray().length == 0) {
                    // 主动消费输入流触发缓存
                    wrapper.getInputStream().readAllBytes();
                }
                return new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BusinessException(ResultCode.SIGN_ERROR, "请求体读取失败");
            }
        }
        return "";
    }
}
