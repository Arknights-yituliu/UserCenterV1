package com.orange.service.impl;

import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.mapper.AccountDeletionMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.AccountDeletionService;
import com.orange.service.OAuthTokenService;
import com.orange.service.RevokeService;
import com.orange.service.EmailCodeService;
import com.orange.entity.po.UserInfo;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 先保存注销审计，再删除用户与游戏绑定；数据库操作处于同一事务。 */
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private final UserInfoMapper userMapper;
    private final AccountDeletionMapper deletionMapper;
    private final OAuthTokenService oauthTokenService;
    private final RevokeService revokeService;
    private final EmailCodeService emailCodeService;

    public AccountDeletionServiceImpl(UserInfoMapper userMapper, AccountDeletionMapper deletionMapper,
                                     OAuthTokenService oauthTokenService, RevokeService revokeService,
                                     EmailCodeService emailCodeService) {
        this.userMapper = userMapper;
        this.deletionMapper = deletionMapper;
        this.oauthTokenService = oauthTokenService;
        this.revokeService = revokeService;
        this.emailCodeService = emailCodeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long uid, String code) {
        deleteUser(uid, "session", UserContext.getClientId(), code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByRefreshToken(String refreshToken, String code) {
        OAuthTokenService.RefreshTokenPrincipal principal = oauthTokenService.resolveRefreshToken(refreshToken);
        deleteUser(principal.getUid(), "oauth_refresh", principal.getClientId(), code);
    }

    @Override
    public void sendDeletionCode(Long uid, String ip) {
        sendCode(uid, UserContext.getClientId(), ip);
    }

    @Override
    public void sendDeletionCodeByRefreshToken(String refreshToken, String ip) {
        OAuthTokenService.RefreshTokenPrincipal principal = oauthTokenService.resolveRefreshToken(refreshToken);
        sendCode(principal.getUid(), principal.getClientId(), ip);
    }

    private void sendCode(Long uid, String clientId, String ip) {
        UserInfo user = uid == null ? null : userMapper.selectById(uid);
        emailCodeService.sendAccountDeletionCode(uid, requireEmail(user), clientId, ip);
    }

    private String requireEmail(UserInfo user) {
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ResultCode.EMAIL_NOT_BOUND);
        }
        return user.getEmail();
    }

    private void deleteUser(Long uid, String authType, String clientId, String code) {
        if (uid == null || uid <= 0) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        // 同一用户的重复注销串行化；先校验，失败时不撤销任何凭证。
        UserInfo user = userMapper.selectByUidForUpdate(uid);
        emailCodeService.verifyAccountDeletionCode(uid, requireEmail(user), clientId, code);
        // 审计必须写入成功才能开始删除；不归档密码哈希和令牌。
        if (deletionMapper.archiveUser(uid, authType, clientId) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "注销审计保存失败，请稍后重试");
        }
        deletionMapper.deleteAkBindings(uid);
        deletionMapper.deleteEfBindings(uid);
        if (userMapper.deleteById(uid) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 只按用户会话索引清理登录会话，不扫描 Redis，也不删除 OAuth/PAT 及其他业务记录。
        // 剩余凭证由账户存在性校验阻止继续使用。Redis 异常会使上述数据库操作全部回滚。
        revokeService.kickAllSessions(uid);
    }
}
