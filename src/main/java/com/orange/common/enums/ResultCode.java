package com.orange.common.enums;

/**
 * 业务错误码枚举
 *
 * <p>分段约定（见设计方案）：200=成功，1xxxx=参数错误，2xxxx=账号错误，
 * 3xxxx=验证码/限流，4xxxx=系统内部，8xxxx=认证/权限/签名</p>
 *
 * @author UserCenter
 */
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),

    // ---------- 1xxxx 参数/格式错误 ----------
    /** 参数错误 */
    PARAM_ERROR(10001, "参数错误"),
    /** 参数校验失败 */
    PARAM_VALID_ERROR(10002, "参数校验失败"),
    /** 非法操作 */
    ILLEGAL_OPERATION(10003, "非法操作"),

    // ---------- 2xxxx 账号错误 ----------
    /** 用户不存在 */
    USER_NOT_FOUND(20001, "用户不存在"),
    /** 密码错误 */
    PASSWORD_ERROR(20002, "密码错误"),
    /** 邮箱已被注册 */
    EMAIL_ALREADY_EXISTS(20003, "该邮箱已被注册"),
    /** 账号被封禁 */
    USER_BANNED(20004, "账号已被封禁"),
    /** 账号名称为空 */
    USERNAME_EMPTY(20005, "账号不能为空"),

    // ---------- 3xxxx 验证码/限流 ----------
    /** 验证码错误 */
    CODE_ERROR(30001, "验证码错误"),
    /** 验证码过期 */
    CODE_EXPIRED(30002, "验证码已过期，请重新获取"),
    /** 验证码发送太频繁 */
    CODE_SEND_TOO_FREQUENT(30003, "验证码发送过于频繁，请稍后再试"),
    /** 登录失败次数过多被锁定 */
    LOGIN_LOCKED(30004, "登录失败次数过多，账号已临时锁定"),
    /** IP 限流 */
    IP_RATE_LIMITED(30005, "请求过于频繁，请稍后再试"),

    // ---------- 4xxxx 系统内部错误 ----------
    /** 系统内部错误 */
    SYSTEM_ERROR(40001, "系统繁忙，请稍后再试"),
    /** 邮件发送失败 */
    MAIL_SEND_FAILED(40002, "验证码发送失败，请稍后再试"),

    // ---------- 8xxxx 认证/权限/签名错误 ----------
    /** 未登录或 token 无效 */
    NOT_LOGIN(80001, "未登录或登录已失效"),
    /** token 已过期 */
    TOKEN_EXPIRED(80002, "登录已过期，请重新登录"),
    /** 签名错误 */
    SIGN_ERROR(80003, "签名校验失败"),
    /** 签名时间戳超窗 */
    SIGN_TIMESTAMP_EXPIRED(80004, "请求已过期，请校准时间"),
    /** 请求重放 */
    REPLAY_ATTACK(80005, "重复请求已被拦截"),
    /** AppId 不存在 */
    APP_NOT_FOUND(80006, "AppId 不存在"),
    /** 应用已停用 */
    APP_DISABLED(80007, "应用已停用"),
    /** 无权限操作 */
    FORBIDDEN(80008, "无权限操作"),

    // ---------- 9xxxx OAuth2 授权错误 ----------
    /** OAuth 客户端不存在或已停用 */
    OAUTH_CLIENT_INVALID(90001, "OAuth 客户端不存在或已停用"),
    /** 回调地址不在白名单内 */
    OAUTH_REDIRECT_URI_INVALID(90002, "回调地址不在白名单内"),
    /** 请求的权限范围未授权 */
    OAUTH_SCOPE_INVALID(90003, "请求的权限范围未授权"),
    /** 授权码无效或已过期 */
    OAUTH_CODE_INVALID(90004, "授权码无效或已过期"),
    /** 授权码已被使用 */
    OAUTH_CODE_REUSED(90005, "授权码已被使用"),
    /** 授权类型不支持 */
    OAUTH_GRANT_INVALID(90006, "授权类型不支持"),
    /** 客户端密钥校验失败 */
    OAUTH_SECRET_INVALID(90007, "客户端密钥校验失败"),
    /** PKCE 校验失败 */
    OAUTH_PKCE_INVALID(90008, "PKCE 校验失败"),
    /** 访问令牌无效或已过期 */
    OAUTH_TOKEN_INVALID(90009, "访问令牌无效或已过期");

    /** 状态码 */
    private final int code;

    /** 默认消息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
