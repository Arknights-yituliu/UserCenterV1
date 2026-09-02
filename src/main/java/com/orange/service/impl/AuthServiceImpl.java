package com.orange.service.impl;

import com.orange.common.util.IdGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.DesensitizeUtil;
import com.orange.common.util.IdGenerator;
import com.orange.common.util.LogUtil;
import com.orange.common.util.OAuthUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RedisRateLimiter;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.SessionInfo;
import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.dto.auth.ResetPasswordRequest;
import com.orange.entity.po.LoginLog;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.auth.LoginVO;
import com.orange.entity.vo.auth.ServerLoginVO;
import com.orange.entity.vo.oauth.DirectLoginSessionVO;
import com.orange.entity.vo.oauth.DirectLoginTicketVO;
import com.orange.mapper.LoginLogMapper;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.AuthService;
import com.orange.service.EmailCodeService;
import com.orange.service.RevokeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
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
    private final OAuthClientMapper oauthClientMapper;
    private final EmailCodeService emailCodeService;
    private final RevokeService revokeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    /** 会话有效期（秒）：默认 180 天 */
    @Value("${user-center.session-ttl-seconds:15552000}")
    private long sessionTtlSeconds;

    /** 登录失败次数上限 */
    @Value("${user-center.login-fail-limit:5}")
    private long loginFailLimit;

    /** 登录锁定时间（秒） */
    @Value("${user-center.login-lock-seconds:900}")
    private long loginLockSeconds;

    /** 直连登录发起会话有效期（秒）：默认 5 分钟 */
    @Value("${user-center.oauth.direct-channel-ttl-seconds:300}")
    private long directChannelTtlSeconds;

    /** 直连登录一次性票据有效期（秒）：默认 60 秒 */
    @Value("${user-center.oauth.direct-ticket-ttl-seconds:60}")
    private long directTicketTtlSeconds;

    /** 直连登录发起会话限流配置。 */
    @Value("${user-center.oauth.direct-rate-limit.session.ip-limit:60}")
    private long directSessionIpLimit = 60;

    @Value("${user-center.oauth.direct-rate-limit.session.client-ip-limit:20}")
    private long directSessionClientIpLimit = 20;

    @Value("${user-center.oauth.direct-rate-limit.session.window-seconds:60}")
    private long directSessionWindowSeconds = 60;

    /** 直连登录提交凭证限流与失败锁定配置。 */
    @Value("${user-center.oauth.direct-rate-limit.login.ip-request-limit:30}")
    private long directLoginIpRequestLimit = 30;

    @Value("${user-center.oauth.direct-rate-limit.login.ip-request-window-seconds:60}")
    private long directLoginIpRequestWindowSeconds = 60;

    @Value("${user-center.oauth.direct-rate-limit.login.failure-window-seconds:900}")
    private long directLoginFailureWindowSeconds = 900;

    @Value("${user-center.oauth.direct-rate-limit.login.first-lock-threshold:5}")
    private long directLoginFirstLockThreshold = 5;

    @Value("${user-center.oauth.direct-rate-limit.login.first-lock-seconds:60}")
    private long directLoginFirstLockSeconds = 60;

    @Value("${user-center.oauth.direct-rate-limit.login.second-lock-threshold:10}")
    private long directLoginSecondLockThreshold = 10;

    @Value("${user-center.oauth.direct-rate-limit.login.second-lock-seconds:300}")
    private long directLoginSecondLockSeconds = 300;

    @Value("${user-center.oauth.direct-rate-limit.login.third-lock-threshold:15}")
    private long directLoginThirdLockThreshold = 15;

    @Value("${user-center.oauth.direct-rate-limit.login.third-lock-seconds:900}")
    private long directLoginThirdLockSeconds = 900;

    /** 直连注册限流配置。 */
    @Value("${user-center.oauth.direct-rate-limit.register.ip-limit:10}")
    private long directRegisterIpLimit = 10;

    @Value("${user-center.oauth.direct-rate-limit.register.email-limit:3}")
    private long directRegisterEmailLimit = 3;

    @Value("${user-center.oauth.direct-rate-limit.register.window-seconds:3600}")
    private long directRegisterWindowSeconds = 3600;

    /**
     * 构造器注入依赖
     *
     * @param userMapper          用户 Mapper
     * @param loginLogMapper      登录日志 Mapper
     * @param oauthClientMapper   OAuth 客户端 Mapper（服务端登录的 client 认证）
     * @param emailCodeService    验证码服务
     * @param revokeService       吊销服务（重设密码后踢全部会话）
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     * @param validator           Bean Validation 校验器（直连注册散参手动校验）
     */
    public AuthServiceImpl(UserInfoMapper userMapper, LoginLogMapper loginLogMapper, OAuthClientMapper oauthClientMapper,
                           EmailCodeService emailCodeService, RevokeService revokeService,
                           StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                           Validator validator) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.oauthClientMapper = oauthClientMapper;
        this.emailCodeService = emailCodeService;
        this.revokeService = revokeService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.validator = validator;
    }

    /**
     * 注册（密码注册 / 邮箱验证码注册），注册成功后直接签发会话登录
     *
     * <p>注册需设置密码（password / email_code 两种方式均要求密码）；邮箱与用户名至少填一个，
     * email_code 方式必须填邮箱。只要填了邮箱，都必须先通过邮箱验证码验证邮箱可用。</p>
     *
     * @param request 注册参数
     * @param ip      注册 IP
     * @param clientId   来源客户端 id
     * @return 登录响应（含 token）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterRequest request, String ip, String clientId) {
        // 校验 + 创建用户（含邮箱验证码校验、唯一性校验、密码加密）
        UserInfo user = createRegisteredUser(request, ip);
        return buildLoginVO(user, createSession(user.getUid(), clientId));
    }

    /**
     * 创建注册用户：校验注册参数（邮箱/用户名至少一个、唯一性、邮箱验证码、密码），
     * 加密密码并落库（主站注册与直连注册共用）
     *
     * @param request 注册参数
     * @param ip      注册 IP
     * @return 已落库的用户实体
     */
    private UserInfo createRegisteredUser(RegisterRequest request, String ip) {
        String email = request.getEmail();
        String userName = request.getUserName();
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasUserName = userName != null && !userName.isBlank();
        // 邮箱与用户名至少提供一个作为登录凭证
        if (!hasEmail && !hasUserName) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱和用户名至少填写一个");
        }
        // 邮箱唯一性校验（填了才校验）
        if (hasEmail && userMapper.selectCount(Wrappers.<UserInfo>lambdaQuery()
                .eq(UserInfo::getEmail, email)) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }
        // 用户名唯一性校验（填了才校验）
        if (hasUserName && userMapper.selectCount(Wrappers.<UserInfo>lambdaQuery()
                .eq(UserInfo::getUserName, userName)) > 0) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        UserInfo user = new UserInfo();
        user.setUid(IdGenerator.getInstance().nextId());
        user.setEmail(hasEmail ? email : null);
        user.setUserName(hasUserName ? userName : null);
        // 昵称缺省时优先用用户名，其次邮箱
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? (hasUserName ? userName : email)
                : request.getNickname());
        user.setIp(ip);
        user.setStatus(1);
        user.setRegisterTime(LocalDateTime.now());

        // 填了邮箱则必须先通过邮箱验证码验证：确保邮箱真实可用且属于注册者本人
        // （密码注册带邮箱、邮箱验证码注册两种形态均适用；仅用户名注册不涉及）
        if (hasEmail) {
            if (request.getVerificationCode() == null || request.getVerificationCode().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "填写邮箱时需提供邮箱验证码");
            }
            emailCodeService.verifyCode(email, request.getVerificationCode());
        }

        if ("password".equals(request.getRegisterType()) || "email_code".equals(request.getRegisterType())) {
            // 统一要求设置密码（password / email_code 两种注册方式均要求）
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "密码不能为空");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            // 邮箱验证码注册必须有邮箱（密码注册可仅用户名；验证码已在上方统一校验）
            if ("email_code".equals(request.getRegisterType()) && !hasEmail) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱验证码注册需填写邮箱");
            }
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的注册方式");
        }

        userMapper.insert(user);
        return user;
    }

    /**
     * 登录（密码 / 邮箱验证码），并记录登录日志
     *
     * @param request 登录参数
     * @param ip      登录 IP
     * @param ua      浏览器 UA
     * @param clientId   来源客户端 id
     * @return 登录响应（含 token）
     */
    @Override
    public LoginVO login(LoginRequest request, String ip, String ua, String clientId) {
        UserInfo user;
        String loginType;

        if (TYPE_PASSWORD.equals(request.getAccountType())) {
            // 账号优先取用户名（兼容旧系统迁移用户），未传时回退邮箱（历史前端只传 email）
            String userName = request.getUserName();
            String account = (userName != null && !userName.isBlank()) ? userName : request.getEmail();
            user = passwordLogin(account, request.getPassword());
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

        String token = createSession(user.getUid(), clientId);
        writeLoginLog(user.getUid(), clientId, loginType, ip, ua, 1);
        return buildLoginVO(user, token);
    }

    /**
     * 发送重设密码验证码到账号绑定的邮箱
     *
     * <p>账号为邮箱或用户名；账号不存在抛用户不存在，未绑定邮箱抛 EMAIL_NOT_BOUND</p>
     *
     * @param account 账号（邮箱或用户名）
     * @param ip      请求 IP（限流维度）
     */
    @Override
    public void sendResetCode(String account, String ip) {
        UserInfo user = findUserByAccount(account);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.EMAIL_NOT_BOUND);
        }
        emailCodeService.sendCode(user.getEmail(), "reset", ip);
    }

    /**
     * 通过邮箱验证码重置密码（校验验证码后更新密码并踢出全部会话）
     *
     * @param request 重设参数（账号 + 验证码 + 新密码）
     */
    @Override
    public void resetPassword(ResetPasswordRequest request) {
        UserInfo user = findUserByAccount(request.getAccount());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.EMAIL_NOT_BOUND);
        }
        emailCodeService.verifyCode(user.getEmail(), request.getCode());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 安全考虑：重置密码后踢出该用户全部会话，需重新登录
        revokeService.kickAllSessions(user.getUid());
    }

    /**
     * 按账号（邮箱或用户名）定位用户
     *
     * @param account 账号
     * @return 用户实体
     */
    private UserInfo findUserByAccount(String account) {
        if (account == null || account.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号不能为空");
        }
        UserInfo user = userMapper.selectOne(Wrappers.<UserInfo>lambdaQuery()
                .and(w -> w.eq(UserInfo::getEmail, account).or().eq(UserInfo::getUserName, account)));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 登出：删除 Redis 会话
     *
     * @param token 会话 token
     */
    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            String sessionJson = stringRedisTemplate.opsForValue().get(RedisKeyUtil.token(token));
            stringRedisTemplate.delete(RedisKeyUtil.token(token));
            if (sessionJson != null) {
                try {
                    SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
                    if (session != null && session.getUid() != null) {
                        stringRedisTemplate.opsForSet().remove(RedisKeyUtil.uidSession(session.getUid()), token);
                    }
                } catch (JsonProcessingException ignored) {
                    LogUtil.warn(AuthServiceImpl.class, "会话反序列化失败，跳过反向索引清理");
                }
            }
        }
    }

    /**
     * 加载 OAuth 客户端并校验启用状态（与直连登录的 client 认证配合使用）
     *
     * @param clientId 客户端 ID
     * @return 客户端实体
     */
    private OAuthClient requireEnabledOAuthClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        OAuthClient client = oauthClientMapper.selectById(clientId);
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        return client;
    }

    /**
     * 客户端认证：需要密钥的客户端必须提供正确密钥（BCrypt 比对，与 OAuth 令牌流程一致）
     *
     * @param client       客户端实体
     * @param clientSecret 请求携带的密钥
     */
    private void authenticateOAuthClient(OAuthClient client, String clientSecret) {
        boolean needsSecret = StringUtils.hasText(client.getClientSecret());
        if (needsSecret && (!StringUtils.hasText(clientSecret)
                || !passwordEncoder.matches(clientSecret, client.getClientSecret()))) {
            throw new BusinessException(ResultCode.OAUTH_SECRET_INVALID);
        }
    }

    /**
     * 直连登录-发起会话：client 认证通过后签发短时发起会话凭证（channel），
     * 前端持 channel 才能调直连登录，避免 client_secret 暴露给浏览器
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @return 发起会话凭证及有效期
     */
    @Override
    public DirectLoginSessionVO createDirectSession(String clientId, String clientSecret, String sourceIp) {
        // 1. 客户端认证：仅登记且启用的 client 可发起
        OAuthClient client = requireEnabledOAuthClient(clientId);
        authenticateOAuthClient(client, clientSecret);
        // 2. 客户端认证通过后，使用一图流转发的来源 IP 进行限流。
        enforceDirectSessionRateLimit(clientId, sourceIp);
        // 3. 签发发起会话凭证（绑定 clientId，短时有效）
        String channel = OAuthUtil.generateToken();
        Map<String, Object> record = new HashMap<>();
        record.put("clientId", clientId);
        writeJson(RedisKeyUtil.directChannel(channel), record, directChannelTtlSeconds);
        DirectLoginSessionVO vo = new DirectLoginSessionVO();
        vo.setChannel(channel);
        vo.setExpiresIn(directChannelTtlSeconds);
        LogUtil.debug(AuthServiceImpl.class, "[Auth] 直连登录发起会话: clientId={}", clientId);
        return vo;
    }

    /**
     * 直连登录-提交凭证（前端直接调用）：持发起会话凭证提交登录凭证（密码或邮箱验证码），
     * 校验通过后签发一次性登录票据（凭证不经过旧系统后端），并发起会话消费即失效
     *
     * @param channel     发起会话凭证
     * @param accountType 登录方式：password=账号密码（默认）/ email=邮箱验证码
     * @param account     登录账号（密码方式为邮箱或用户名；邮箱方式为邮箱）
     * @param password    明文密码（密码方式必填）
     * @param code        邮箱验证码（邮箱方式必填）
     * @return 一次性登录票据及有效期
     */
    @Override
    public DirectLoginTicketVO directLogin(String channel, String accountType, String account, String password,
                                           String code, String sourceIp) {
        // 1. 先按来源 IP 与账号进行限流，避免无效 channel 请求绕开风控。
        UserInfo user;
        String type = StringUtils.hasText(accountType) ? accountType : TYPE_PASSWORD;
        if (!TYPE_PASSWORD.equals(type) && !TYPE_EMAIL_CODE.equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的登录方式");
        }
        String normalizedAccount = normalizeRateTarget(account);
        enforceDirectLoginRateLimit(sourceIp, normalizedAccount);
        // 2. 校验发起会话凭证有效（前端必须持旧系统后端换取的 channel）
        String clientId = readDirectChannel(channel);
        // 3. 按登录方式校验凭证（密码/邮箱验证码，复用主登录逻辑：含失败锁定与失败计数）
        try {
            if (TYPE_PASSWORD.equals(type)) {
                user = passwordLogin(account, password);
            } else {
                user = emailCodeLogin(account, code);
            }
            checkUserStatus(user);
        } catch (BusinessException e) {
            recordDirectLoginFailure(sourceIp, normalizedAccount);
            throw e;
        }
        clearDirectLoginAccountFailure(normalizedAccount);
        // 4. 消费发起会话（一次性，防止重复使用）
        stringRedisTemplate.delete(RedisKeyUtil.directChannel(channel));
        // 5. 签发一次性登录票据（绑定 clientId + uid，短时有效）
        LogUtil.debug(AuthServiceImpl.class, "[Auth] 直连登录成功: clientId={}, uid={}, type={}", clientId, user.getUid(), type);
        return issueDirectTicket(clientId, user.getUid());
    }

    /**
     * 直连注册（前端直接调用）：持发起会话凭证提交注册信息，创建用户后签发一次性登录票据
     * （注册凭证不经过旧系统后端），旧系统后端凭票据兑换用户信息
     *
     * @param channel 发起会话凭证
     * @param request 注册参数（方式/邮箱/用户名/密码/验证码/昵称）
     * @param ip      注册 IP
     * @return 一次性登录票据及有效期
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DirectLoginTicketVO directRegister(String channel, RegisterRequest request, String ip) {
        // 1. 先按来源 IP 和邮箱限流，验证码发送频控由 EmailCodeService 统一执行。
        enforceDirectRegisterRateLimit(ip, request.getEmail());
        // 2. 校验发起会话凭证有效（前端必须持旧系统后端换取的 channel）
        String clientId = readDirectChannel(channel);
        // 3. 手动 Bean Validation：直连注册为散参入参未走 @Valid，此处补齐与主站注册一致的格式/长度校验
        validateRegisterRequest(request);
        // 4. 复用注册校验与创建用户逻辑（含邮箱验证码校验、唯一性校验、密码加密）
        UserInfo user = createRegisteredUser(request, ip);
        // 5. 消费发起会话（一次性，防止重复使用）
        stringRedisTemplate.delete(RedisKeyUtil.directChannel(channel));
        // 6. 签发一次性登录票据（绑定 clientId + uid），旧系统后端凭票兑换用户信息
        LogUtil.debug(AuthServiceImpl.class, "[Auth] 直连注册成功: clientId={}, uid={}", clientId, user.getUid());
        return issueDirectTicket(clientId, user.getUid());
    }

    /**
     * 手动触发注册参数校验：直连注册为散参入参、未走 @Valid 注解，需手动校验，
     * 使格式/长度规则与主站注册（@RequestBody @Valid RegisterRequest）完全一致
     *
     * @param request 注册参数
     */
    private void validateRegisterRequest(RegisterRequest request) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            // 仅返回第一条违规信息，避免向调用方暴露全部校验细节
            String message = violations.iterator().next().getMessage();
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    /**
     * 校验并读取发起会话凭证，返回绑定的 clientId（不消费，消费由调用方成功后执行）
     *
     * @param channel 发起会话凭证
     * @return 绑定的客户端 ID
     */
    private String readDirectChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录会话凭证缺失");
        }
        Map<String, Object> channelRecord = readJsonMap(RedisKeyUtil.directChannel(channel));
        if (channelRecord == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录会话不存在或已过期，请重新发起");
        }
        String clientId = (String) channelRecord.get("clientId");
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录会话无效");
        }
        return clientId;
    }

    private void enforceDirectSessionRateLimit(String clientId, String sourceIp) {
        String ip = normalizeRateTarget(sourceIp);
        if (!RedisRateLimiter.tryAcquire(stringRedisTemplate, RedisKeyUtil.rate("direct-session-ip", ip),
                directSessionIpLimit, directSessionWindowSeconds)
                || !RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("direct-session-client-ip", clientId + ":" + ip),
                directSessionClientIpLimit, directSessionWindowSeconds)) {
            throw new BusinessException(ResultCode.IP_RATE_LIMITED);
        }
    }

    private void enforceDirectLoginRateLimit(String sourceIp, String account) {
        String ip = normalizeRateTarget(sourceIp);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeyUtil.rate("direct-login-ip-lock", ip)))
                || !RedisRateLimiter.tryAcquire(stringRedisTemplate, RedisKeyUtil.rate("direct-login-ip", ip),
                directLoginIpRequestLimit, directLoginIpRequestWindowSeconds)) {
            throw new BusinessException(ResultCode.IP_RATE_LIMITED);
        }
        if (!account.isEmpty() && Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                RedisKeyUtil.rate("direct-login-account-lock", account)))) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }
    }

    private void recordDirectLoginFailure(String sourceIp, String account) {
        applyProgressiveDirectLoginLock("direct-login-ip-fail", "direct-login-ip-lock",
                normalizeRateTarget(sourceIp));
        if (!account.isEmpty()) {
            applyProgressiveDirectLoginLock("direct-login-account-fail", "direct-login-account-lock", account);
        }
    }

    private void applyProgressiveDirectLoginLock(String failureBiz, String lockBiz, String target) {
        String failureKey = RedisKeyUtil.rate(failureBiz, target);
        Long count = stringRedisTemplate.opsForValue().increment(failureKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failureKey, directLoginFailureWindowSeconds, TimeUnit.SECONDS);
        }
        long lockSeconds = count == null ? 0
                : count >= directLoginThirdLockThreshold ? directLoginThirdLockSeconds
                : count >= directLoginSecondLockThreshold ? directLoginSecondLockSeconds
                : count >= directLoginFirstLockThreshold ? directLoginFirstLockSeconds : 0;
        if (lockSeconds > 0) {
            stringRedisTemplate.opsForValue().set(RedisKeyUtil.rate(lockBiz, target), "1", lockSeconds,
                    TimeUnit.SECONDS);
        }
    }

    private void clearDirectLoginAccountFailure(String account) {
        if (!account.isEmpty()) {
            stringRedisTemplate.delete(RedisKeyUtil.rate("direct-login-account-fail", account));
            stringRedisTemplate.delete(RedisKeyUtil.rate("direct-login-account-lock", account));
        }
    }

    private void enforceDirectRegisterRateLimit(String sourceIp, String email) {
        String ip = normalizeRateTarget(sourceIp);
        if (!RedisRateLimiter.tryAcquire(stringRedisTemplate, RedisKeyUtil.rate("direct-register-ip", ip),
                directRegisterIpLimit, directRegisterWindowSeconds)) {
            throw new BusinessException(ResultCode.IP_RATE_LIMITED);
        }
        String normalizedEmail = normalizeRateTarget(email);
        if (!normalizedEmail.isEmpty() && !RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("direct-register-email", normalizedEmail), directRegisterEmailLimit,
                directRegisterWindowSeconds)) {
            throw new BusinessException(ResultCode.IP_RATE_LIMITED);
        }
    }

    private String normalizeRateTarget(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 签发一次性登录票据（绑定 clientId + uid）
     *
     * @param clientId 客户端 ID
     * @param uid      用户 uid
     * @return 一次性登录票据
     */
    private DirectLoginTicketVO issueDirectTicket(String clientId, Long uid) {
        String ticket = OAuthUtil.generateToken();
        Map<String, Object> record = new HashMap<>();
        record.put("clientId", clientId);
        record.put("uid", uid);
        writeJson(RedisKeyUtil.directTicket(ticket), record, directTicketTtlSeconds);
        DirectLoginTicketVO vo = new DirectLoginTicketVO();
        vo.setTicket(ticket);
        vo.setExpiresIn(directTicketTtlSeconds);
        return vo;
    }

    /**
     * 直连登录-兑换用户信息（旧系统后端调用）：凭一次性票据兑换用户信息，
     * 校验票据归属该 client 且未被消费（并发/重放下仅一次成功）
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @param ticket       一次性登录票据
     * @return 用户信息（uid/昵称/头像/脱敏邮箱/状态）
     */
    @Override
    public ServerLoginVO directUser(String clientId, String clientSecret, String ticket) {
        // 1. 客户端认证
        OAuthClient client = requireEnabledOAuthClient(clientId);
        authenticateOAuthClient(client, clientSecret);
        if (!StringUtils.hasText(ticket)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录票据缺失");
        }
        // 2. 一次性占用标记：只有第一个请求能拿到 true，重放直接拒绝
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyUtil.directUsed(ticket), "1", directTicketTtlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录票据已使用");
        }
        // 3. 读取票据并删除数据（配合 used 标记双保险）
        Map<String, Object> record = readJsonMap(RedisKeyUtil.directTicket(ticket));
        if (record == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录票据不存在或已过期");
        }
        stringRedisTemplate.delete(RedisKeyUtil.directTicket(ticket));
        // 4. 校验票据归属该 client，防止跨客户端冒用
        if (!clientId.equals(record.get("clientId"))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "登录票据不属于该客户端");
        }
        Object uid = record.get("uid");
        if (!(uid instanceof Number)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "登录票据无效");
        }
        // 5. 组装响应：邮箱脱敏
        UserInfo user = userMapper.selectById(((Number) uid).longValue());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        ServerLoginVO vo = new ServerLoginVO();
        vo.setUid(user.getUid());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(DesensitizeUtil.maskEmail(user.getEmail()));
        vo.setStatus(user.getStatus());
        LogUtil.debug(AuthServiceImpl.class, "[Auth] 直连登录兑换成功: clientId={}, uid={}", clientId, user.getUid());
        return vo;
    }

    /**
     * 写 JSON 记录到 Redis 并设置过期时间
     *
     * @param key   Redis key
     * @param value 待序列化对象
     * @param ttl   过期秒数
     */
    private void writeJson(String key, Object value, long ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "数据序列化失败");
        }
    }

    /**
     * 从 Redis 读取 JSON 记录并反序列化为 Map
     *
     * @param key Redis key
     * @return 反序列化结果，key 不存在返回 null
     */
    private Map<String, Object> readJsonMap(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 签发会话：生成 token 并写入 Redis（设备数不限，删除 key 即踢下线）
     *
     * @param uid   用户 uid
     * @param clientId 来源客户端 id（可为空）
     * @return 会话 token
     */
    @Override
    public String createSession(Long uid, String clientId) {
        String token = SignUtil.generateToken();
        SessionInfo session = new SessionInfo(uid, clientId == null ? "" : clientId, LocalDateTime.now());
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyUtil.token(token),
                    objectMapper.writeValueAsString(session),
                    sessionTtlSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.opsForSet().add(RedisKeyUtil.uidSession(uid), token);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "会话创建失败");
        }
        return token;
    }

    /**
     * 密码登录：校验登录锁定、BCrypt 密码、失败计数
     *
     * @param account  登录账号（邮箱或用户名）
     * @param password 明文密码
     * @return 用户实体
     */
    private UserInfo passwordLogin(String account, String password) {
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名/邮箱和密码不能为空");
        }
        // 账号可能是邮箱或用户名，两者均尝试匹配（兼容旧系统迁移用户）
        UserInfo user = userMapper.selectOne(Wrappers.<UserInfo>lambdaQuery()
                .and(w -> w.eq(UserInfo::getEmail, account).or().eq(UserInfo::getUserName, account)));
        if (user == null) {
            // 用户不存在也累计失败次数，防止撞库探测
            recordLoginFail(account);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 登录锁定校验
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeyUtil.loginLock(account)))) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordLoginFail(account);
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        clearLoginFail(account);
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
            user.setUid(IdGenerator.getInstance().nextId());
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
     * @param clientId   来源客户端 id
     * @param loginType 登录方式
     * @param ip        登录 IP
     * @param ua        UA
     * @param status    1=成功 0=失败
     */
    private void writeLoginLog(Long uid, String clientId, String loginType, String ip, String ua, int status) {
        LoginLog log = new LoginLog();
        log.setUid(uid);
        log.setClientId(clientId);
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
        vo.setEmail(DesensitizeUtil.maskEmail(user.getEmail()));
        return vo;
    }
}
