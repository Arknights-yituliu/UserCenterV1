package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户默认展示的明日方舟数据来源偏好实体（ak_default_source）
 *
 * <p>每用户最多一条（uk_default_source_uid），只记录「默认返回哪个来源」的指向，
 * 不复制任何干员数据。{@code source} 取值与接口一致：skland 森空岛自动导入、
 * manual 用户手动上传；{@code akUid} 为对应来源的归属键，manual 存用户中心 UID。</p>
 *
 * @author UserCenter
 */
@TableName("ak_default_source")
public class AkDefaultSource {

    /** 数据库自增行 ID，主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户中心 UID，与唯一键 uk_default_source_uid 对应 */
    private Long uid;

    /** 数据来源：skland 森空岛自动导入，manual 用户手动上传 */
    private String source;

    /** 对应来源的归属键：skland 为游戏账号 UID，manual 为用户中心 UID */
    private String akUid;

    /** 首次设置默认来源的时间 */
    private LocalDateTime createTime;

    /** 最近一次修改默认来源的时间 */
    private LocalDateTime updateTime;

    /**
     * 获取数据库自增主键
     *
     * @return 主键 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据库自增主键
     *
     * @param id 主键 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取偏好所属的用户中心 UID
     *
     * @return 用户中心 UID
     */
    public Long getUid() {
        return uid;
    }

    /**
     * 设置偏好所属的用户中心 UID
     *
     * @param uid 用户中心 UID
     */
    public void setUid(Long uid) {
        this.uid = uid;
    }

    /**
     * 获取数据来源
     *
     * @return skland 或 manual
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置数据来源
     *
     * @param source skland 或 manual
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 获取对应来源的归属键
     *
     * @return skland 为游戏账号 UID，manual 为用户中心 UID
     */
    public String getAkUid() {
        return akUid;
    }

    /**
     * 设置对应来源的归属键
     *
     * @param akUid skland 为游戏账号 UID，manual 为用户中心 UID
     */
    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    /**
     * 获取首次设置默认来源的时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置首次设置默认来源的时间
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取最近一次修改默认来源的时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置最近一次修改默认来源的时间
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
