package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * OAuth 客户端 CORS Origin 记录（oauth_client_origin）。
 *
 * <p>每个客户端当前登记一个 Origin。Origin 与客户端审批相互独立，新增或修改后必须
 * 由管理员单独审批，审批通过且启用的记录才会进入运行时 CORS 缓存。</p>
 *
 * @author UserCenter
 */
@TableName("oauth_client_origin")
public class OAuthClientOrigin {

    /** OAuth 客户端 ID，同时作为主键，保证当前一个客户端只有一条 Origin 记录。 */
    @TableId(type = IdType.INPUT)
    private String clientId;

    /** 客户端名称，冗余保存以便管理员审核时直接展示。 */
    private String clientName;

    /** 规范化后的 Origin（scheme + host + 可选非默认端口）。 */
    private String origin;

    /** 所有者是否启用该 Origin：1=启用，0=停用。 */
    private Integer enabled;

    /** 管理员是否审批通过：1=通过，0=待审批或拒绝。 */
    private Integer adminApproved;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getAdminApproved() {
        return adminApproved;
    }

    public void setAdminApproved(Integer adminApproved) {
        this.adminApproved = adminApproved;
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
}
