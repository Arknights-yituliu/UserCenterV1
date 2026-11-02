package com.orange.entity.dto.efpool;

import java.util.List;

/**
 * 武器寻访记录分页数据 DTO
 *
 * @author UserCenter
 */
public class EfWeaponPoolRecordDataDTO {

    /** 寻访记录列表 */
    private List<EfWeaponPoolRecordDTO> list;

    /** 是否还有更多数据 */
    private Boolean hasMore;

    public List<EfWeaponPoolRecordDTO> getList() {
        return list;
    }

    public void setList(List<EfWeaponPoolRecordDTO> list) {
        this.list = list;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
