package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户自定义 OAuth 授权范围实体（oauth_user_scope）
 *
 * <p>与 {@link OAuthGrant} 的区别：授权台账一行对应一次令牌签发，本表一行对应用户与应用的组合，
 * 保存用户主动挑选并愿意长期授予该应用的权限集合。签发令牌时若存在本记录，
 * 取「本次申请范围 ∩ 本记录范围」作为最终授权范围，使用户在确认页的裁剪与追加在下次登录时自动延续。</p>
 *
 * @author UserCenter
 */
@TableName("oauth_user_scope")
public class OAuthUserScope {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 uid */
    private Long uid;

    /** OAuth 客户端 ID */
    private String clientId;

    /** 用户自定义授予的范围（英文逗号分隔，不可为空） */
    private String scopes;

    /** 记录创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 记录更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
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
