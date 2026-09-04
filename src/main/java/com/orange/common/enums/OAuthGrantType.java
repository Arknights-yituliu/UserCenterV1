package com.orange.common.enums;

import com.orange.common.exception.BusinessException;

/**
 * OAuth2 授权类型白名单。
 *
 * <p>本服务只实现授权码和刷新令牌两种流程。把允许值集中在枚举中，可以让注册、
 * 授权、换码和刷新使用同一套判断，避免出现“数据库登记了但服务端并未实现”的配置。</p>
 */
public enum OAuthGrantType {

    /** 授权码模式；客户端必须登记该类型才能发起授权和兑换授权码。 */
    AUTHORIZATION_CODE("authorization_code"),

    /** 刷新令牌模式；未登记时不会签发 refresh_token。 */
    REFRESH_TOKEN("refresh_token");

    private final String value;

    OAuthGrantType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 使用协议原值进行严格匹配，不自动修正大小写或首尾空格。
     *
     * @param candidate 待判断值
     * @return 完全一致时返回 true
     */
    public boolean matches(String candidate) {
        return value.equals(candidate);
    }

    /**
     * 判断授权类型是否在系统白名单中。
     *
     * @param candidate 待判断值
     * @return 是否支持
     */
    public static boolean supports(String candidate) {
        for (OAuthGrantType grantType : values()) {
            if (grantType.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将协议值转换为枚举；未知值作为注册参数错误直接拒绝。
     *
     * @param value 协议值
     * @return 对应枚举
     */
    public static OAuthGrantType fromValue(String value) {
        for (OAuthGrantType grantType : values()) {
            if (grantType.matches(value)) {
                return grantType;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的授权类型: " + value);
    }
}
