package com.orange.entity.vo.efpool;

import com.orange.entity.po.EfCharacterPoolRecord;
import com.orange.entity.po.EfWeaponPoolRecord;

import java.util.List;

/**
 * 终末地寻访记录视图对象：一次返回该角色的干员与武器寻访记录
 *
 * @author UserCenter
 */
public class EfPoolRecordVO {

    /** 干员寻访记录 */
    private List<EfCharacterPoolRecord> characterPoolRecord;

    /** 武器寻访记录 */
    private List<EfWeaponPoolRecord> weaponPoolRecord;

    public EfPoolRecordVO() {
    }

    public EfPoolRecordVO(List<EfCharacterPoolRecord> characterPoolRecord, List<EfWeaponPoolRecord> weaponPoolRecord) {
        this.characterPoolRecord = characterPoolRecord;
        this.weaponPoolRecord = weaponPoolRecord;
    }

    public List<EfCharacterPoolRecord> getCharacterPoolRecord() {
        return characterPoolRecord;
    }

    public void setCharacterPoolRecord(List<EfCharacterPoolRecord> characterPoolRecord) {
        this.characterPoolRecord = characterPoolRecord;
    }

    public List<EfWeaponPoolRecord> getWeaponPoolRecord() {
        return weaponPoolRecord;
    }

    public void setWeaponPoolRecord(List<EfWeaponPoolRecord> weaponPoolRecord) {
        this.weaponPoolRecord = weaponPoolRecord;
    }
}
