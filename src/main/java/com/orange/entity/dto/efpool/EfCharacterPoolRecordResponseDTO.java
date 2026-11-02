package com.orange.entity.dto.efpool;

/**
 * 干员寻访记录接口响应 DTO
 *
 * <p>额外携带本次请求的卡池类型，便于异步回调时把记录落库到正确的卡池分类。</p>
 *
 * @author UserCenter
 */
public class EfCharacterPoolRecordResponseDTO {

    /** 响应码，0 表示成功 */
    private Integer code;

    /** 响应数据 */
    private EfCharacterPoolRecordDataDTO data;

    /** 响应消息 */
    private String msg;

    /** 本次请求的卡池类型（非上游字段，由本地补充） */
    private String poolType;

    public EfCharacterPoolRecordResponseDTO() {
    }

    public EfCharacterPoolRecordResponseDTO(Integer code, EfCharacterPoolRecordDataDTO data, String msg, String poolType) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.poolType = poolType;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public EfCharacterPoolRecordDataDTO getData() {
        return data;
    }

    public void setData(EfCharacterPoolRecordDataDTO data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getPoolType() {
        return poolType;
    }

    public void setPoolType(String poolType) {
        this.poolType = poolType;
    }
}
