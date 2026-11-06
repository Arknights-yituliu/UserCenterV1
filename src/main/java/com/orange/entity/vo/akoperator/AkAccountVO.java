package com.orange.entity.vo.akoperator;

import java.time.LocalDateTime;

/**
 * 当前用户已绑定的游戏账号视图对象（不含干员正文）
 *
 * <p>列表把用户设置的默认来源排在首位，其余按 {@code updateTime} 倒序，
 * 因此不指定账号时前端取首条即可展示默认数据。
 * 条目来源由 {@code source} 区分：森空岛自动导入的账号使用游戏账号 UID；
 * 手动上传条目没有游戏账号，{@code akUid} 复用用户中心 UID，且仅在用户存在手动数据时返回。</p>
 *
 * @author UserCenter
 */
public class AkAccountVO {

    /** 来源：森空岛自动导入 */
    public static final String SOURCE_SKLAND = "skland";

    /** 来源：用户手动上传 */
    public static final String SOURCE_MANUAL = "manual";

    /** 游戏账号 UID；手动上传条目复用用户中心 UID */
    private String akUid;

    /** 数据来源：skland 森空岛自动导入，manual 用户手动上传 */
    private String source;

    /** 数据创建时间：森空岛为绑定关系创建时间；手动上传条目没有创建时间，固定为 null */
    private LocalDateTime createTime;

    /** 数据最近一次变更时间：森空岛为最近一次导入时间，手动版为最近一次保存时间 */
    private LocalDateTime updateTime;

    /** 是否为用户设置的默认数据来源：true 表示读取干员数据不传 akUid 时默认返回该条目 */
    private boolean defaultFlag;

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 是否为用户设置的默认数据来源
     *
     * @return true 表示该条目是默认来源
     */
    public boolean isDefaultFlag() {
        return defaultFlag;
    }

    /**
     * 设置是否为用户设置的默认数据来源
     *
     * @param defaultFlag true 表示该条目是默认来源
     */
    public void setDefaultFlag(boolean defaultFlag) {
        this.defaultFlag = defaultFlag;
    }
}
