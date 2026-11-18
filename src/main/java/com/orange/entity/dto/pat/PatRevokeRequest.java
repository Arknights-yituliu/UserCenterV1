package com.orange.entity.dto.pat;

import jakarta.validation.constraints.NotNull;

/**
 * 按 id 撤销一条个人访问令牌的请求参数
 *
 * <p>删除按 {@code id + uid} 双条件执行：跨用户传别人的 id 视为成功但不删任何行。</p>
 *
 * @author UserCenter
 */
public class PatRevokeRequest {

    /** 令牌记录主键 */
    @NotNull(message = "id 不能为空")
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
