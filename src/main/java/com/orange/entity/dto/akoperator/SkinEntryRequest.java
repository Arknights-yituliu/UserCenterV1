package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 单条皮肤数据请求参数
 *
 * <p>对应森空岛 player/info 的 {@code data.skins[]}：{@code id} 与 {@code ts}。
 * 皮肤编码形如 {@code char_4064_mlynar@epoque#28}，区分大小写，只允许可见 ASCII 字符；
 * 获得时间允许缺省，服务端归一化为 0 表示「时间未知」。</p>
 *
 * @author UserCenter
 */
public class SkinEntryRequest {

    /** 皮肤编码，缺失或空必须拒绝，不能归一化为 0 */
    @NotBlank(message = "皮肤ID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,128}$", message = "皮肤ID必须为不超过128位的可见ASCII字符")
    private String skinId;

    /** 获得时间 Unix 秒，缺省按 0（时间未知）处理 */
    @Min(value = 0, message = "gainTs不能为负数")
    private Integer gainTs;

    public String getSkinId() {
        return skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId;
    }

    public Integer getGainTs() {
        return gainTs;
    }

    public void setGainTs(Integer gainTs) {
        this.gainTs = gainTs;
    }
}
