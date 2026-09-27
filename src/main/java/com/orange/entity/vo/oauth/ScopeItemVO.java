package com.orange.entity.vo.oauth;

/**
 * OAuth 权限条目：权限标识 + 中文描述
 *
 * <p>供授权确认页与用户授权管理页统一展示权限，描述取自 {@code OAuthScope} 枚举，
 * 未识别的标识原样返回，避免界面出现空白项。</p>
 *
 * @author UserCenter
 */
public class ScopeItemVO {

    /** 权限标识（如 user.read） */
    private String code;

    /** 权限中文描述 */
    private String desc;

    /**
     * 无参构造，供 JSON 反序列化使用
     */
    public ScopeItemVO() {
    }

    /**
     * 构造权限条目
     *
     * @param code 权限标识
     * @param desc 权限中文描述
     */
    public ScopeItemVO(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
