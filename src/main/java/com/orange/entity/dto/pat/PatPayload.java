package com.orange.entity.dto.pat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PAT payload 的自定义结构（请求入参 / 列表响应 / 消费端解析共用）
 *
 * <p>DB 侧仍是 {@code tlc_pat.payload TEXT}，只存序列化后的 JSON 文本；本类是「用的时候」
 * 反序列化的目标类型。<strong>所有字段都可为空</strong>：调用方可以完全不传 payload、
 * 传 {@code {}}、或只填其中几个字段，签发阶段不做任何必填校验，缺失字段保持 {@code null}。</p>
 *
 * <p><strong>宽松反序列化</strong>：{@code @JsonIgnoreProperties(ignoreUnknown = true)} 让
 * DB 里历史数据或更新版本写入的、本类尚未定义的键被静默忽略；本类已定义但 JSON 里没有的
 * 字段保持 {@code null}。因此后续新增字段不需要迁移存量数据，读到旧行也不会因多出键而报错。
 * 代价是列表与签发响应只回传本类已定义的字段，不再「原样返回任意 KV」。</p>
 *
 * <p>字段都是 {@code String}。注意 Jackson 对 String 字段默认允许标量强转：JSON 里写
 * 整型数字会转成十进制字符串，布尔与浮点也会分别转成 {@code "true"} / {@code "1.5"}，
 * 对象与数组则在绑定阶段直接报错。{@link com.orange.common.util.PatPayloadUtil} 负责
 * trim 与「绝不返回空串」，账号归属最终仍由业务层的绑定校验把关。</p>
 *
 * <p><strong>扩展方式</strong>：加字段时同步更新 {@link #isEmpty()} 即可；DB 列、Mapper、
 * 序列化代码都不用动，历史行里缺失的字段自动为 {@code null}。</p>
 *
 * @author UserCenter
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatPayload {

    /** 游戏账号 UID 在 JSON 里的字段名（DB 文本与对外接口统一用蛇形） */
    public static final String FIELD_GAME_UID = "game_uid";

    /** 游戏名称在 JSON 里的字段名（DB 文本与对外接口统一用蛇形） */
    public static final String FIELD_GAME_NAME = "game_name";

    /** 终末地服务器 ID 在 JSON 里的字段名 */
    public static final String FIELD_SERVER_ID = "server_id";

    /** 令牌来源在 JSON 里的字段名 */
    public static final String FIELD_SOURCE = "source";

    /**
     * 游戏数据接口要读取的账号 UID（明日方舟账号 UID / 终末地角色 roleId），可空。
     *
     * <p>另接受驼峰写法 {@code gameUid} 作为反序列化别名；序列化输出固定为 {@code game_uid}。</p>
     */
    @JsonProperty(FIELD_GAME_UID)
    @JsonAlias("gameUid")
    private String gameUid;

    /** 游戏名称，可空；当前不参与鉴权与取数，仅作为扩展信息随签发与列表透传 */
    @JsonProperty(FIELD_GAME_NAME)
    @JsonAlias("gameName")
    private String gameName;

    /** 终末地服务器 ID，可空；读取终末地数据时与 game_uid 一起定位角色，缺省按空串处理 */
    @JsonProperty(FIELD_SERVER_ID)
    @JsonAlias("serverId")
    private String serverId;

    /**
     * 令牌来源标识，可空。
     *
     * <p>由签发方自行填写，用于区分同一用户名下不同渠道/场景签出的令牌（如 {@code web}、
     * {@code mini_app}、{@code script}），便于在列表里辨认与后续按来源统计。服务端不校验取值、
     * 不参与鉴权与取数，仅随签发、列表与消费端解析原样透传；长度由 payload 整体 ≤ 4KB 兜底。</p>
     */
    @JsonProperty(FIELD_SOURCE)
    private String source;

    /**
     * 是否所有字段都为空（等价于不传 payload）
     *
     * <p>用于签发时把「全空对象」归一化为 DB 的 {@code NULL}，与改造前 {@code {}} 落库为
     * NULL 的口径一致。</p>
     *
     * @return true=没有任何有效字段
     */
    @JsonIgnore
    public boolean isEmpty() {
        return gameUid == null && gameName == null && serverId == null && source == null;
    }

    public String getGameUid() {
        return gameUid;
    }

    public void setGameUid(String gameUid) {
        this.gameUid = gameUid;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
