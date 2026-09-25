package com.orange.common.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 平台支持的 OAuth 授权范围。
 *
 * <p>该枚举是 scope 标识、前端展示文案和敏感级别的唯一来源。客户端注册时只能
 * 选择这里声明的范围；实际令牌仍必须是客户端登记范围与本次授权请求的交集。</p>
 *
 * <p>{@link #ALL} 是元范围（通配符）：它被标记为 {@code manualOnly}，不会出现在注册 /
 * 编辑可选项也不出现在 scope 元数据接口里，只能由管理员直接写库授予，且仅对机密客户端生效。</p>
 *
 * @author UserCenter
 */
public enum OAuthScope {

    /** 读取用户基础资料。 */
    USER_READ("user.read", "基础资料", "读取你的用户 ID、昵称和头像", false, false),

    /** 读取并修改用户个人资料。 */
    USER_PROFILE("user.profile", "个人资料", "读取并修改你的个人资料", true, false),

    /** 读取当前客户端名下的用户配置。 */
    CONFIG_READ("config.read", "读取应用配置", "读取该应用名下的用户配置", false, false),

    /** 写入或删除当前客户端名下的用户配置。 */
    CONFIG_WRITE("config.write", "管理应用配置", "创建、修改或删除该应用名下的用户配置", false, false),

    /** 读取用户游戏数据。 */
    GAMA_DATA_READ("gama-data.read", "游戏数据", "读取用户游戏数据", true, false),

    /** 写入用户游戏数据。 */
    GAMA_DATA_WRITE("gama-data.write", "游戏数据", "写入用户游戏数据", true, false),

    /** 全部授权范围（通配符），仅允许管理员手工写库授予。 */
    ALL("all", "全部权限", "访问平台全部接口（仅限管理员手工授予）", true, true);

    private final String code;
    private final String name;
    private final String description;
    private final boolean sensitive;
    private final boolean manualOnly;

    OAuthScope(String code, String name, String description, boolean sensitive, boolean manualOnly) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.sensitive = sensitive;
        this.manualOnly = manualOnly;
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
     * 是否仅允许管理员手工写库配置。
     *
     * @return true 表示不允许通过注册 / 编辑接口提交
     */
    public boolean isManualOnly() {
        return manualOnly;
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

    /**
     * 返回可供客户端注册 / 编辑选择的授权范围（排除仅限手工写库的元范围）。
     *
     * @return 可选项列表，顺序与枚举声明一致
     */
    public static List<OAuthScope> selectable() {
        return Arrays.stream(values())
                .filter(scope -> !scope.manualOnly)
                .collect(Collectors.toList());
    }
}
