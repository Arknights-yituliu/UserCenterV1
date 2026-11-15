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
 * <p>范围之间存在上下级关系：游戏级范围（{@code ak-data.read}、{@code ef-data.read} 等）
 * 的上级是平台级的 {@code gama-data.read} / {@code gama-data.write}。持有上级范围即视为
 * 持有其全部子范围，因此登记了 {@code gama-data.read} 的存量应用仍可访问全部游戏接口。</p>
 *
 * @author UserCenter
 */
public enum OAuthScope {

    /** 读取用户基础资料。 */
    USER_READ("user.read", "基础资料", "读取你的用户 ID、昵称和头像", false, false),

    /** 读取并修改用户个人资料。 */
    USER_PROFILE("user.profile", "个人资料", "读取并修改你的个人资料", true, false),

    /** 读取当前客户端名下的用户配置，以及用户自己的排班表。 */
    CONFIG_READ("config.read", "读取应用配置", "读取该应用名下的用户配置，以及查看你的排班表", false, false),

    /** 写入或删除当前客户端名下的用户配置，以及保存或删除用户排班表。 */
    CONFIG_WRITE("config.write", "管理应用配置", "创建、修改或删除该应用名下的用户配置，以及保存或删除你的排班表", false, false),

    /** 读取用户游戏数据。 */
    GAMA_DATA_READ("gama-data.read", "游戏数据", "读取用户游戏数据", true, false),

    /** 写入用户游戏数据。 */
    GAMA_DATA_WRITE("gama-data.write", "游戏数据", "写入用户游戏数据", true, false),

    /** 读取明日方舟数据，是 {@link #GAMA_DATA_READ} 的子范围。 */
    AK_DATA_READ("ak-data.read", "明日方舟数据访问", "读取你的明日方舟账号、干员、仓库与抽卡数据",
            true, false, GAMA_DATA_READ),

    /** 写入明日方舟数据，是 {@link #GAMA_DATA_WRITE} 的子范围。 */
    AK_DATA_WRITE("ak-data.write", "写入明日方舟数据", "保存或删除你的明日方舟账号、干员与仓库数据",
            true, false, GAMA_DATA_WRITE),

    /** 读取终末地数据，是 {@link #GAMA_DATA_READ} 的子范围。 */
    EF_DATA_READ("ef-data.read", "终末地数据访问", "读取你的终末地角色、玩家数据与寻访记录",
            true, false, GAMA_DATA_READ),

    /** 写入终末地数据，是 {@link #GAMA_DATA_WRITE} 的子范围。 */
    EF_DATA_WRITE("ef-data.write", "写入终末地数据", "保存你的终末地角色与玩家数据、创建寻访记录导入任务",
            true, false, GAMA_DATA_WRITE),

    /** 全部授权范围（通配符），仅允许管理员手工写库授予。 */
    ALL("all", "全部权限", "访问平台全部接口（仅限管理员手工授予）", true, true);

    private final String code;
    private final String name;
    private final String description;
    private final boolean sensitive;
    private final boolean manualOnly;

    /** 上级范围：持有上级范围即视为持有本范围，用于粗粒度权限覆盖细粒度权限。 */
    private final OAuthScope parent;

    OAuthScope(String code, String name, String description, boolean sensitive, boolean manualOnly) {
        this(code, name, description, sensitive, manualOnly, null);
    }

    OAuthScope(String code, String name, String description, boolean sensitive, boolean manualOnly,
               OAuthScope parent) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.sensitive = sensitive;
        this.manualOnly = manualOnly;
        this.parent = parent;
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
     * 获取上级范围。
     *
     * @return 上级范围；顶层范围返回 null
     */
    public OAuthScope getParent() {
        return parent;
    }

    /**
     * 判断一个已授予的 scope 是否覆盖所需范围。
     *
     * <p>覆盖规则：同一标识、元范围 {@link #ALL}，或沿 {@link #parent} 链命中上级范围。
     * 因此持有 {@code gama-data.read} 的令牌可以访问两个游戏的数据，而只持有
     * {@code ak-data.read} 的令牌不能访问终末地接口。</p>
     *
     * @param grantedCode 令牌实际已授予的 scope 标识（可为 null / 空白）
     * @param required    接口所需的 scope
     * @return true 表示已获得该范围
     */
    public static boolean covers(String grantedCode, OAuthScope required) {
        if (grantedCode == null || required == null) {
            return false;
        }
        OAuthScope granted = findByCode(grantedCode).orElse(null);
        if (granted == null) {
            return false;
        }
        if (granted == ALL) {
            return true;
        }
        for (OAuthScope cursor = required; cursor != null; cursor = cursor.parent) {
            if (cursor == granted) {
                return true;
            }
        }
        return false;
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
     * 返回可由非管理员选择的授权范围（排除仅限管理员手工写库授予的元范围）。
     *
     * <p>用于客户端注册 / 编辑的可选项，以及用户在确认页 / 授权管理页可追加的范围；
     * 元范围 {@link #ALL} 不属于此列，只能由管理员直接写库授予。</p>
     *
     * @return 可选项列表，顺序与枚举声明一致
     */
    public static List<OAuthScope> selectable() {
        return Arrays.stream(values())
                .filter(scope -> !scope.manualOnly)
                .collect(Collectors.toList());
    }
}
