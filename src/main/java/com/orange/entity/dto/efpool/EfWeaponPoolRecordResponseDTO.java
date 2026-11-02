package com.orange.entity.dto.efpool;

/**
 * 武器寻访记录接口响应 DTO
 *
 * @author UserCenter
 */
public class EfWeaponPoolRecordResponseDTO {

    /** 响应码，0 表示成功 */
    private Integer code;

    /** 响应数据 */
    private EfWeaponPoolRecordDataDTO data;

    /** 响应消息 */
    private String msg;

    public EfWeaponPoolRecordResponseDTO() {
    }

    public EfWeaponPoolRecordResponseDTO(Integer code, EfWeaponPoolRecordDataDTO data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public EfWeaponPoolRecordDataDTO getData() {
        return data;
    }

    public void setData(EfWeaponPoolRecordDataDTO data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
