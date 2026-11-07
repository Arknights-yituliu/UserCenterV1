package com.orange.entity.vo.akoperator;

/**
 * 单条皮肤读取结果
 *
 * <p>只返回拥有关系与获得时间；皮肤名称、画师、系列、立绘等元数据不在本服务内，
 * 由调用方用 {@code skinId} 关联游戏静态数据（skin_table.json）自行补全。</p>
 *
 * @author UserCenter
 */
public class SkinVO {

    /** 皮肤编码 */
    private String skinId;

    /** 获得时间 Unix 秒，0 表示时间未知 */
    private Integer gainTs;

    public SkinVO() {
    }

    /**
     * 构造单条皮肤读取结果
     *
     * @param skinId 皮肤编码
     * @param gainTs 获得时间 Unix 秒，0 表示时间未知
     */
    public SkinVO(String skinId, Integer gainTs) {
        this.skinId = skinId;
        this.gainTs = gainTs;
    }

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
