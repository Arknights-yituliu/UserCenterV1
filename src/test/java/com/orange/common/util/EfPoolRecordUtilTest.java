package com.orange.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.entity.dto.efpool.EfCharacterPoolRecordDTO;
import com.orange.entity.dto.efpool.EfCharacterPoolRecordResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 寻访记录赠礼映射规则单元测试：覆盖正常抽卡、赠礼记录与脏数据三分支
 *
 * @author UserCenter
 */
class EfPoolRecordUtilTest {

    /** 与 Spring Boot 默认行为保持一致：忽略未知字段，上游新增字段不影响解析 */
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * 正常抽卡记录原样保留角色编码与名称
     */
    @Test
    void keepsCharacterRecordFields() {
        EfCharacterPoolRecordDTO dto = characterRecord();

        assertThat(EfPoolRecordUtil.isUnusableRecord(dto)).isFalse();
        assertThat(EfPoolRecordUtil.isGiftRecord(dto)).isFalse();
        assertThat(EfPoolRecordUtil.resolveCharId(dto)).isEqualTo("chr_0019_karin");
        assertThat(EfPoolRecordUtil.resolveCharName(dto)).isEqualTo("秋栗");
    }

    /**
     * 赠礼记录以 kind 作为伪角色编码，名称按映射表转换
     */
    @Test
    void mapsGiftRecordToPseudoCharacter() {
        EfCharacterPoolRecordDTO dto = giftRecord("gift_intel_book");

        assertThat(EfPoolRecordUtil.isUnusableRecord(dto)).isFalse();
        assertThat(EfPoolRecordUtil.isGiftRecord(dto)).isTrue();
        assertThat(EfPoolRecordUtil.resolveCharId(dto)).isEqualTo("gift_intel_book");
        assertThat(EfPoolRecordUtil.resolveCharName(dto)).isEqualTo("寻访情报书");
    }

    /**
     * 未收录的 kind 原样作为名称返回，便于发现问题后补映射
     */
    @Test
    void keepsUnknownKindAsDisplayName() {
        EfCharacterPoolRecordDTO dto = giftRecord("gift_unknown_item");

        assertThat(EfPoolRecordUtil.resolveCharId(dto)).isEqualTo("gift_unknown_item");
        assertThat(EfPoolRecordUtil.resolveCharName(dto)).isEqualTo("gift_unknown_item");
    }

    /**
     * 既无角色编码也无记录类型的记录判定为脏数据
     */
    @Test
    void marksRecordWithoutCharIdAndKindAsUnusable() {
        assertThat(EfPoolRecordUtil.isUnusableRecord(giftRecord(null))).isTrue();
        assertThat(EfPoolRecordUtil.isUnusableRecord(giftRecord("   "))).isTrue();
        assertThat(EfPoolRecordUtil.isUnusableRecord(null)).isTrue();
        assertThat(EfPoolRecordUtil.isGiftRecord(giftRecord(null))).isFalse();
    }

    /**
     * 按上游真实返回结构解析：赠礼记录没有 charId，只在 kind 上带记录类型
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    void resolvesGiftRecordParsedFromUpstreamJson() throws Exception {
        String json = """
                {"code":0,"msg":"","data":{"hasMore":false,"list":[
                  {"poolId":"special","poolName":"特许寻访","charId":"chr_0019_karin","charName":"秋栗",
                   "rarity":4,"isFree":false,"isNew":false,"gachaTs":"1769073164712","seqId":"61"},
                  {"poolId":"special","poolName":"特许寻访","kind":"gift_intel_book",
                   "gachaTs":"1769073164800","seqId":"62"}
                ]}}
                """;

        EfCharacterPoolRecordResponseDTO response =
                OBJECT_MAPPER.readValue(json, EfCharacterPoolRecordResponseDTO.class);
        List<EfCharacterPoolRecordDTO> list = response.getData().getList();

        assertThat(list).hasSize(2);
        assertThat(EfPoolRecordUtil.isGiftRecord(list.get(0))).isFalse();
        assertThat(EfPoolRecordUtil.resolveCharId(list.get(1))).isEqualTo("gift_intel_book");
        assertThat(EfPoolRecordUtil.resolveCharName(list.get(1))).isEqualTo("寻访情报书");
        assertThat(list.get(1).getCharId()).isNull();
    }

    /**
     * 构造一条正常抽卡记录
     *
     * @return 寻访记录 DTO
     */
    private EfCharacterPoolRecordDTO characterRecord() {
        EfCharacterPoolRecordDTO dto = new EfCharacterPoolRecordDTO();
        dto.setPoolId("special");
        dto.setPoolName("特许寻访");
        dto.setCharId("chr_0019_karin");
        dto.setCharName("秋栗");
        dto.setRarity(4);
        dto.setIsFree(false);
        dto.setIsNew(false);
        dto.setGachaTs("1769073164712");
        dto.setSeqId("61");
        return dto;
    }

    /**
     * 构造一条赠礼记录：只有 kind，没有角色编码与名称
     *
     * @param kind 记录类型
     * @return 寻访记录 DTO
     */
    private EfCharacterPoolRecordDTO giftRecord(String kind) {
        EfCharacterPoolRecordDTO dto = new EfCharacterPoolRecordDTO();
        dto.setPoolId("special");
        dto.setPoolName("特许寻访");
        dto.setKind(kind);
        dto.setGachaTs("1769073164800");
        dto.setSeqId("62");
        return dto;
    }
}
