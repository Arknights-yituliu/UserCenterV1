package com.orange.service;

import com.orange.entity.dto.oauth.LegacyImportTokenItem;

import java.util.List;

/**
 * 旧系统只读令牌导入服务（临时接口）
 *
 * <p>用于一次性把旧系统签发的只读令牌批量迁移进 {@code oauth_custom_token} 表，
 * 统一挂到固定客户端 {@code arknights-yituliu-web} 名下。存量导入结束后该接口应关闭。</p>
 *
 * @author UserCenter
 */
public interface OAuthLegacyImportService {

    /** 旧系统令牌归属的固定客户端 ID */
    String LEGACY_CLIENT_ID = "arknights-yituliu-web";

    /**
     * 批量导入旧系统只读令牌
     *
     * <p>先做密钥比对（密钥为配置中的固定字符串，字符串相等即通过），
     * 通过后逐条校验 uid/token/name/scope/expires_in/expire_time 的格式与必填，非法条目跳过并计入失败数；
     * 合法条目批量写入 {@code oauth_custom_token} 表，client_id 固定为 {@link #LEGACY_CLIENT_ID}。</p>
     *
     * @param importKey 调用方提交的密钥（请求头 X-Import-Key）
     * @param items     待导入的令牌条目列表
     * @return 导入结果（成功入库条数与跳过条数）
     */
    LegacyImportResult importTokens(String importKey, List<LegacyImportTokenItem> items);

    /**
     * 导入结果
     *
     * @param imported 成功入库条数
     * @param skipped  因格式非法被跳过的条数
     */
    record LegacyImportResult(int imported, int skipped) {
    }
}
