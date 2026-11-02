package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 单条物品存量请求参数
 *
 * <p>物品编码为 1-64 位可见 ASCII 字符串，数量为非负整数（JSON 数字类型），
 * 服务端直接按 long 落库。</p>
 *
 * @author UserCenter
 */
public class ItemEntryRequest {

    /** 稳定物品编码，对应 JSON 中的 itemId */
    @NotBlank(message = "物品ID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,64}$", message = "物品ID必须为不超过64位的可见ASCII字符")
    private String itemId;

    /** 物品数量，非负整数（JSON 数字类型），最大 18 位十进制 */
    @NotNull(message = "物品数量不能为空")
    @Min(value = 0, message = "物品数量不能为负数")
    @Max(value = 999999999999999999L, message = "物品数量超出范围")
    private Long count;

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
