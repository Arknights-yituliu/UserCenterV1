package com.orange.entity.dto.efpool;

import java.util.List;

/**
 * 干员寻访记录分页数据 DTO
 *
 * @author UserCenter
 */
public class EfCharacterPoolRecordDataDTO {

    /** 寻访记录列表 */
    private List<EfCharacterPoolRecordDTO> list;

    /** 是否还有更多数据 */
    private Boolean hasMore;

    public List<EfCharacterPoolRecordDTO> getList() {
        return list;
    }

    public void setList(List<EfCharacterPoolRecordDTO> list) {
        this.list = list;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public void setHasMore(Boolean hasMore) {
        this.hasMore = hasMore;
    }
}
