package com.orange.common.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终末地寻访记录相关工具类：请求头、seq_id 分页序列与 token 编码
 *
 * @author UserCenter
 */
public final class EfPoolRecordUtil {

    private EfPoolRecordUtil() {
    }

    /**
     * 构造请求终末地寻访记录接口所需的请求头
     *
     * <p>不设置 Host 与 Accept-Encoding：JDK HttpClient 禁止手动设置 Host，
     * 且不会自动解压响应体，交给客户端使用默认值即可。</p>
     *
     * @return 请求头
     */
    public static Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.put("Sec-Fetch-Dest", "empty");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:146.0) Gecko/20100101 Firefox/146.0");
        return headers;
    }

    /**
     * 构造 seq_id 递减序列，用于按页往回拉取寻访记录
     *
     * <p>上游每页返回 5 条记录，因此从最新 seq_id 开始每次递减 5，直到已保存的最大 seq_id。
     * 已保存的最大值大于 10 时再往前多取一页，防止两次导入之间出现断层。</p>
     *
     * @param lastSeqIdStr  最新一页最后一条记录的 seq_id
     * @param existingSeqId 数据库中已保存的最大 seq_id，可为 null
     * @return seq_id 列表
     */
    public static List<String> initSeqIdList(String lastSeqIdStr, Integer existingSeqId) {
        if (lastSeqIdStr == null || lastSeqIdStr.isBlank()) {
            throw new IllegalArgumentException("lastSeqIdStr不能为空");
        }
        int existing = existingSeqId == null ? 0 : existingSeqId;
        if (existing > 10) {
            existing -= 5;
        }
        int lastSeqId = Integer.parseInt(lastSeqIdStr.trim());
        List<String> seqIdList = new ArrayList<>();
        for (int i = lastSeqId; i >= existing; i -= 5) {
            seqIdList.add(String.valueOf(i));
        }
        return seqIdList;
    }

    /**
     * 对 u8Token 做 URL 编码，避免 token 中的保留字符破坏查询串
     *
     * @param token 原始 token
     * @return 编码后的 token；入参为空时原样返回
     */
    public static String encodeToken(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
