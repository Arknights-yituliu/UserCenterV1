package com.orange.common.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 平台支持的 OAuth 授权范围。
 *
 * <p>该枚举是 scope 标识、前端展示文案和敏感级别的唯一来源。客户端注册时只能
 * 选择这里声明的范围；实际令牌仍必须是客户端登记范围与本次授权请求的交集。</p>
 *
 * @author UserCenter
 */
public enum OAuthScope {

    /** 读取用户基础资料。 */
    USER_READ("user.read", "基础资料", "读取你的用户 ID、昵称和头像", false),

    /** 读取并修改用户个人资料。 */
    USER_PROFILE("user.profile", "个人资料", "读取并修改你的个人资料", true),

    /** 读取当前客户端名下的用户配置。 */
    CONFIG_READ("config.read", "读取应用配置", "读取该应用名下的用户配置", false),

    /** 写入或删除当前客户端名下的用户配置。 */
    CONFIG_WRITE("config.write", "管理应用配置", "创建、修改或删除该应用名下的用户配置", false),

    /** 读取用户游戏数据。 */
    GAMA_DATA_READ("gama-data.read", "游戏数据", "读取用户游戏数据", true),

    /** 写入用户游戏数据。 */
    GAMA_DATA_WRITE("gama-data.write", "游戏数据", "写入用户游戏数据", true);

    private final String code;
    private final String name;
    private final String description;
    private final boolean sensitive;

    OAuthScope(String code, String name, String description, boolean sensitive) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.sensitive = sensitive;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    /**
     * 依据协议标识查找 scope；匹配严格区分大小写且不自动去除空白。
     *
     * @param code scope 标识
     * @return 对应范围；未知值返回空
     */
    public static Optional<OAuthScope> findByCode(String code) {
        return Arrays.stream(values()).filter(scope -> scope.code.equals(code)).findFirst();
    }
}
