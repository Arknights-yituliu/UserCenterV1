package com.orange.common.enums;

import com.orange.common.exception.BusinessException;

/**
 * OAuth2 客户端认证方式白名单。
 *
 * <p>这里刻意只声明令牌端点已经真正实现的认证方式。认证方式属于客户端的
 * 安全属性，不能通过 {@code client_secret} 是否为空来推断，否则数据库中的声明
 * 与运行时行为可能不一致。</p>
 *
 * <p>匹配采用完全相等语义，不自动 trim，也不忽略大小写。这样可以阻止同一认证
 * 方式以多种文本写入数据库，便于后续审计和迁移。</p>
 */
public enum OAuthClientAuthMethod {

    /** 公共客户端，不生成也不校验客户端密钥。 */
    NONE("none"),

    /** 加密客户端，通过表单参数提交客户端密钥。 */
    CLIENT_SECRET_POST("client_secret_post");

    private final String value;

    OAuthClientAuthMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 判断外部值是否与当前枚举严格匹配。
     *
     * @param candidate 待判断值
     * @return 完全一致时返回 true
     */
    public boolean matches(String candidate) {
        return value.equals(candidate);
    }

    /**
     * 判断认证方式是否在系统白名单中。
     *
     * @param candidate 待判断值
     * @return 是否支持
     */
    public static boolean supports(String candidate) {
        for (OAuthClientAuthMethod method : values()) {
            if (method.matches(candidate)) {
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
    public static OAuthClientAuthMethod fromValue(String value) {
        for (OAuthClientAuthMethod method : values()) {
            if (method.matches(value)) {
                return method;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的客户端认证方式: " + value);
    }
}
