package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.HttpClientUtil;
import com.orange.common.util.LogUtil;
import com.orange.entity.vo.skland.SklandCredTokenVO;
import com.orange.entity.vo.skland.SklandPlayerBindingItemVO;
import com.orange.entity.vo.skland.SklandPlayerBindingResultVO;
import com.orange.entity.vo.skland.SklandQrCheckVO;
import com.orange.entity.vo.skland.SklandQrCreateVO;
import com.orange.service.SklandAuthService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 森空岛鉴权服务实现（对应 BackEndV3 的 SklandHgTokenServiceImpl 与 HypergryphServiceImpl 链路）
 *
 * <p>所有上游接口均为第三方公开接口，失败统一转为业务码 40005，并把上游 msg 透传给前端
 * （例如"需要进行设备验证"），便于用户按提示处理。</p>
 *
 * @author UserCenter
 */
@Service
public class SklandAuthServiceImpl implements SklandAuthService {

    /** 森空岛（国服）域名：凭证签发与游戏数据接口 */
    private static final String SKLAND_DOMAIN = "https://zonai.skland.com";

    /** 鹰角通行证域名：OAuth 授权与扫码登录接口 */
    private static final String HYPERGRYPH_DOMAIN = "https://as.hypergryph.com";

    /** grant 接口：用通行证 token 换取一次性 code */
    private static final String GRANT_URL = HYPERGRYPH_DOMAIN + "/user/oauth2/v2/grant";

    /** 一次性 code 换取 cred/token 接口 */
    private static final String GENERATE_CRED_BY_CODE_URL = SKLAND_DOMAIN + "/web/v1/user/auth/generate_cred_by_code";

    /** 申请扫码二维码接口 */
    private static final String GEN_SCAN_LOGIN_URL = HYPERGRYPH_DOMAIN + "/general/v1/gen_scan/login";

    /** 扫码状态查询接口 */
    private static final String SCAN_STATUS_URL = HYPERGRYPH_DOMAIN + "/general/v1/scan_status";

    /** 一次性 scanCode 换取通行证 token 接口 */
    private static final String TOKEN_BY_SCAN_CODE_URL = HYPERGRYPH_DOMAIN + "/user/auth/v1/token_by_scan_code";

    /** 玩家绑定接口路径：参与签名计算，必须是纯路径（不含域名） */
    private static final String PLAYER_BINDING_PATH = "/api/v1/game/player/binding";

    /** 森空岛应用 code */
    private static final String APP_CODE = "4ca99fa6b56cc2ba";

    /** 设备 id（与官方网页端一致） */
    private static final String DEVICE_ID = "bebd9eee5ad0411dacaee5075792ea2a";

    /** 森空岛网页端 dId */
    private static final String WEB_D_ID = "BqbjCY5HN8T+MFmjvDT4ceaUO6zSMuvdts2+67sjAL4QTA3GfE7M1rDkuUo8Hbhl03557VponqkJW2Z1wh+/nYA==";

    /** 通用 UA */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0";

    /** 玩家绑定签名使用的 dId（与 zonai 客户端一致） */
    private static final String SIGN_D_ID = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0";

    /** 玩家绑定签名使用的客户端版本号 */
    private static final String SIGN_VERSION = "1.2.0";

    /** 客户端平台标识（3 = Android） */
    private static final String PLATFORM = "3";

    /** 扫码二维码 deep link 前缀，上游未返回 scanUrl 时用 scanId 拼接 */
    private static final String DEEP_LINK_PREFIX = "hypergryph://scan_login?scanId=";

    /** 明日方舟在绑定列表中的 appCode */
    private static final String ARKNIGHTS_APP_CODE = "arknights";

    private final ObjectMapper objectMapper;

    /**
     * 构造器注入 JSON 序列化器
     *
     * @param objectMapper Jackson 序列化器（Spring Boot 自动配置的 Bean）
     */
    public SklandAuthServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 通过鹰角官网 token 换取森空岛 cred 与签名 token
     *
     * @param hgToken 鹰角通行证 token
     * @return 森空岛凭证与签名密钥
     */
    @Override
    public SklandCredTokenVO getCredAndTokenByHgToken(String hgToken) {
        String code = grant(hgToken);
        return generateCredByCode(code);
    }

    /**
     * 申请扫码登录二维码
     *
     * @return 扫码会话（scanId + 二维码内容）
     */
    @Override
    public SklandQrCreateVO createQrLogin() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appCode", APP_CODE);

        JsonNode resp = postJson(GEN_SCAN_LOGIN_URL, buildScanHeaders(), body);
        if (resp == null || resp.path("status").asInt(-1) != 0 || resp.path("data").isMissingNode()) {
            throw outerError("申请扫码二维码", resp);
        }
        String scanId = resp.path("data").path("scanId").asText(null);
        if (scanId == null || scanId.isBlank()) {
            throw outerError("申请扫码二维码", resp);
        }
        // scanUrl 可能缺省，缺省时用 scanId 自行拼接 deep link
        String scanUrl = resp.path("data").path("scanUrl").asText(null);
        String qrContent = (scanUrl != null && !scanUrl.isBlank()) ? scanUrl : DEEP_LINK_PREFIX + scanId;

        SklandQrCreateVO vo = new SklandQrCreateVO();
        vo.setScanId(scanId);
        vo.setQrContent(qrContent);
        return vo;
    }

    /**
     * 查询扫码状态，用户确认后自动换取森空岛凭证
     *
     * @param scanId 扫码会话 ID
     * @return 扫码状态（非 0 继续轮询，0 时携带 cred/token）
     */
    @Override
    public SklandQrCheckVO checkQrLogin(String scanId) {
        String encodedScanId = URLEncoder.encode(scanId, StandardCharsets.UTF_8);
        JsonNode resp = getJson(SCAN_STATUS_URL + "?scanId=" + encodedScanId, buildScanHeaders());
        if (resp == null) {
            throw outerError("查询扫码状态", null);
        }
        int status = resp.path("status").asInt(-1);

        SklandQrCheckVO vo = new SklandQrCheckVO();
        vo.setStatus(status);
        vo.setMsg(resp.path("msg").asText(null));

        // 用户已确认：用 scanCode 换通行证 token，再换森空岛凭证
        if (status == 0) {
            String scanCode = resp.path("data").path("scanCode").asText(null);
            if (scanCode == null || scanCode.isBlank()) {
                LogUtil.error(SklandAuthServiceImpl.class, "扫码确认成功但缺少 scanCode");
                throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR);
            }
            String hgToken = exchangeHgToken(scanCode);
            SklandCredTokenVO credToken = getCredAndTokenByHgToken(hgToken);
            vo.setCred(credToken.getCred());
            vo.setToken(credToken.getToken());
        }
        return vo;
    }

    /**
     * 通过鹰角官网 token 换取森空岛凭证并查询明日方舟角色绑定
     *
     * @param hgToken 鹰角通行证 token
     * @return 森空岛凭证与角色绑定列表
     */
    @Override
    public SklandPlayerBindingResultVO getPlayerBindingsByHgToken(String hgToken) {
        SklandCredTokenVO credToken = getCredAndTokenByHgToken(hgToken);

        String timestamp = String.valueOf((System.currentTimeMillis() - 800) / 1000);
        Map<String, String> signParams = new LinkedHashMap<>();
        signParams.put("platform", PLATFORM);
        signParams.put("timestamp", timestamp);
        signParams.put("dId", SIGN_D_ID);
        signParams.put("vName", SIGN_VERSION);
        String signText = PLAYER_BINDING_PATH + timestamp + toJson(signParams);
        String sign = md5Hex(hmacSha256Hex(credToken.getToken(), signText));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("platform", PLATFORM);
        headers.put("timestamp", timestamp);
        headers.put("dId", SIGN_D_ID);
        headers.put("vName", SIGN_VERSION);
        headers.put("cred", credToken.getCred());
        headers.put("sign", sign);

        JsonNode resp = getJson(SKLAND_DOMAIN + PLAYER_BINDING_PATH, headers);
        if (resp == null || resp.path("code").asInt(-1) != 0) {
            throw outerError("查询玩家绑定", resp);
        }

        List<SklandPlayerBindingItemVO> bindings = parseArknightsBindings(resp.path("data").path("list"));

        SklandPlayerBindingResultVO vo = new SklandPlayerBindingResultVO();
        vo.setCred(credToken.getCred());
        vo.setToken(credToken.getToken());
        vo.setPlayerBindingList(bindings);
        return vo;
    }

    /**
     * 从绑定列表响应中提取明日方舟角色绑定
     *
     * @param list 上游 data.list 节点
     * @return 明日方舟角色绑定列表，无 arknights 条目时返回空列表
     */
    private List<SklandPlayerBindingItemVO> parseArknightsBindings(JsonNode list) {
        List<SklandPlayerBindingItemVO> bindings = new ArrayList<>();
        if (list == null || !list.isArray()) {
            return bindings;
        }
        for (JsonNode appNode : list) {
            if (!ARKNIGHTS_APP_CODE.equals(appNode.path("appCode").asText())) {
                continue;
            }
            JsonNode bindingList = appNode.path("bindingList");
            if (!bindingList.isArray()) {
                continue;
            }
            for (JsonNode binding : bindingList) {
                SklandPlayerBindingItemVO item = new SklandPlayerBindingItemVO();
                item.setUid(binding.path("uid").asText(null));
                item.setNickName(binding.path("nickName").asText(null));
                item.setChannelMasterId(binding.path("channelMasterId").asText(null));
                item.setChannelName(binding.path("channelName").asText(null));
                item.setIsDefault(binding.path("isDefault").asBoolean(false));
                bindings.add(item);
            }
        }
        return bindings;
    }

    /**
     * 用一次性 scanCode 换取鹰角通行证 token
     *
     * @param scanCode 扫码确认后的一次性授权码
     * @return 鹰角通行证 token
     */
    private String exchangeHgToken(String scanCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scanCode", scanCode);

        JsonNode resp = postJson(TOKEN_BY_SCAN_CODE_URL, buildScanHeaders(), body);
        String token = resp == null ? null : resp.path("data").path("token").asText(null);
        if (token == null || token.isBlank()) {
            throw outerError("扫码换通行证 token", resp);
        }
        return token;
    }

    /**
     * 第一步：用通行证 token 调 grant 接口换取一次性 code
     *
     * @param hgToken 鹰角通行证 token
     * @return 一次性 code
     */
    private String grant(String hgToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", hgToken);
        body.put("appCode", APP_CODE);
        body.put("type", 0);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("X-DeviceModel", "Firefox");
        headers.put("X-DeviceType", "7");
        headers.put("X-OSVer", "Windows");
        headers.put("X-DeviceId", DEVICE_ID);
        headers.put("X-Captcha-Version", "4.0");

        JsonNode resp = postJson(GRANT_URL, headers, body);
        String code = resp == null ? null : resp.path("data").path("code").asText(null);
        if (code == null || code.isBlank()) {
            throw outerError("grant 换取 code", resp);
        }
        return code;
    }

    /**
     * 第二步：用一次性 code 换取 cred 与签名 token
     *
     * @param code 第一步换取的一次性 code
     * @return 森空岛凭证与签名密钥
     */
    private SklandCredTokenVO generateCredByCode(String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", 1);
        body.put("code", code);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("platform", PLATFORM);
        headers.put("vName", "1.0.0");
        headers.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        headers.put("dId", WEB_D_ID);

        JsonNode resp = postJson(GENERATE_CRED_BY_CODE_URL, headers, body);
        String cred = resp == null ? null : resp.path("data").path("cred").asText(null);
        String token = resp == null ? null : resp.path("data").path("token").asText(null);
        if (resp == null || resp.path("code").asInt(-1) != 0 || cred == null || cred.isBlank()
                || token == null || token.isBlank()) {
            throw outerError("code 换 cred/token", resp);
        }

        SklandCredTokenVO vo = new SklandCredTokenVO();
        vo.setCred(cred);
        vo.setToken(token);
        return vo;
    }

    /**
     * 构建扫码接口通用请求头（参考官方前端 user.hypergryph.com）
     *
     * @return 请求头
     */
    private Map<String, String> buildScanHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", "https://user.hypergryph.com");
        headers.put("Referer", "https://user.hypergryph.com/");
        return headers;
    }

    /**
     * 发送 POST JSON 请求并解析响应
     *
     * @param url     请求地址
     * @param headers 请求头
     * @param body    JSON 请求体（有序 Map 保证字段顺序）
     * @return 解析后的响应；无响应或非法 JSON 时返回 null
     */
    private JsonNode postJson(String url, Map<String, String> headers, Map<String, Object> body) {
        return parseResponse(HttpClientUtil.postJson(url, headers, toJson(body)), url);
    }

    /**
     * 发送 GET 请求并解析响应
     *
     * @param url     请求地址（含 query）
     * @param headers 请求头
     * @return 解析后的响应；无响应或非法 JSON 时返回 null
     */
    private JsonNode getJson(String url, Map<String, String> headers) {
        return parseResponse(HttpClientUtil.get(url, headers), url);
    }

    /**
     * 解析上游响应体
     *
     * @param responseText 响应体原文
     * @param url          请求地址（仅用于日志定位）
     * @return JSON 响应；响应为空或解析失败时返回 null
     */
    private JsonNode parseResponse(String responseText, String url) {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(responseText);
        } catch (IOException e) {
            LogUtil.warn(SklandAuthServiceImpl.class, "第三方响应解析失败，url={}", url);
            return null;
        }
    }

    /**
     * 序列化为 JSON 字符串
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * 构造第三方调用失败异常：上游返回 msg 时透传给前端，便于提示用户处理（如需要进行设备验证）
     *
     * @param action 动作描述，用于日志
     * @param resp   上游响应，可能为 null
     * @return 业务异常
     */
    private BusinessException outerError(String action, JsonNode resp) {
        String upstreamStatus = resp == null
                ? "无响应"
                : resp.path("status").asText("-") + "/" + resp.path("code").asText("-");
        LogUtil.warn(SklandAuthServiceImpl.class, "森空岛{}失败，上游状态={}", action, upstreamStatus);
        String msg = resp == null ? null : resp.path("msg").asText(null);
        if (msg == null || msg.isBlank()) {
            return new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR);
        }
        return new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "森空岛返回：" + msg);
    }

    /**
     * HMAC-SHA256 计算并输出小写十六进制字符串
     *
     * @param key  密钥（森空岛签名 token）
     * @param text 原文
     * @return 十六进制摘要
     */
    private String hmacSha256Hex(String key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * MD5 计算并输出小写十六进制字符串
     *
     * @param text 原文
     * @return 十六进制摘要
     */
    private String md5Hex(String text) {
        try {
            return bytesToHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    /**
     * 字节数组转小写十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
