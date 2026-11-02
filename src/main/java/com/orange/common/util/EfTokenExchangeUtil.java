package com.orange.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.entity.dto.efpool.EfEndfieldUserInfoDTO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 终末地 token 换取工具类：基于鹰角通行证 token 直接换取终末地玩家信息
 *
 * <p>替代原先独立部署的本地 token 换取服务（endfield-yituliu-backend，端口 10086），
 * 内部按顺序完成三步调用：</p>
 * <ol>
 *   <li>调用鹰角 OAuth2 grant 接口，用 hgToken 换取授权 token；</li>
 *   <li>调用绑定列表接口，筛选终末地（endfield）绑定，取 uid、roleId、昵称；</li>
 *   <li>调用 u8_token_by_uid 接口，用授权 token 与 uid 换取 u8Token。</li>
 * </ol>
 *
 * <p>任一步骤失败（网络异常、响应解析失败、业务字段缺失）均返回 null，
 * 由调用方统一处理失败场景。日志不输出任何 token 内容，避免凭证泄露。</p>
 *
 * @author UserCenter
 */
public final class EfTokenExchangeUtil {

    /** 鹰角 OAuth2 授权接口地址 */
    private static final String OAUTH_GRANT_API = "https://as.hypergryph.com/user/oauth2/v2/grant";

    /** 账号绑定列表接口地址 */
    private static final String BINDING_LIST_API =
            "https://binding-api-account-prod.hypergryph.com/account/binding/v1/binding_list";

    /** u8Token 换取接口地址 */
    private static final String U8_TOKEN_API =
            "https://binding-api-account-prod.hypergryph.com/account/binding/v1/u8_token_by_uid";

    /** OAuth2 grant 请求使用的 appCode（鹰角通行证网页端） */
    private static final String GRANT_APP_CODE = "be36d44aa36bfb5b";

    /** OAuth2 grant 请求使用的授权类型 */
    private static final int GRANT_TYPE = 1;

    /** 绑定列表中筛选的终末地应用编码 */
    private static final String ENDFIELD_APP_CODE = "endfield";

    /** JSON 解析器：ObjectMapper 线程安全，静态复用 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EfTokenExchangeUtil() {
    }

    /**
     * 把鹰角通行证 token 换成终末地玩家信息（uid、roleId、昵称、u8Token）
     *
     * @param hgToken 鹰角通行证 token
     * @return 玩家信息；任一步骤失败时返回 null
     */
    public static EfEndfieldUserInfoDTO exchange(String hgToken) {
        String oauthToken = exchangeOAuthToken(hgToken);
        if (oauthToken == null) {
            return null;
        }

        JsonNode endfieldBinding = findEndfieldBinding(oauthToken);
        if (endfieldBinding == null) {
            LogUtil.warn(EfTokenExchangeUtil.class, "通行证下未找到可用的终末地绑定信息");
            return null;
        }
        String uid = endfieldBinding.path("uid").asText("");
        JsonNode role = pickDefaultRole(endfieldBinding);
        if (role == null) {
            LogUtil.warn(EfTokenExchangeUtil.class, "终末地绑定信息中未找到可用角色，uid={}", uid);
            return null;
        }

        String u8Token = requestU8Token(oauthToken, uid);
        if (u8Token == null) {
            return null;
        }

        EfEndfieldUserInfoDTO userInfo = new EfEndfieldUserInfoDTO();
        userInfo.setUid(uid);
        userInfo.setRoleId(role.path("roleId").asText(""));
        userInfo.setNickName(role.path("nickName").asText(""));
        userInfo.setU8Token(u8Token);
        LogUtil.info(EfTokenExchangeUtil.class, "换取终末地角色信息成功，uid={}，roleId={}",
                uid, userInfo.getRoleId());
        return userInfo;
    }

    /**
     * 第一步：用 hgToken 调用 OAuth2 grant 接口换取授权 token
     *
     * @param hgToken 鹰角通行证 token
     * @return 授权 token；请求失败或响应缺少 token 时返回 null
     */
    private static String exchangeOAuthToken(String hgToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", hgToken == null ? "" : hgToken);
        body.put("appCode", GRANT_APP_CODE);
        body.put("type", GRANT_TYPE);
        String responseText = HttpClientUtil.postJson(OAUTH_GRANT_API, buildHeaders(),
                writeJson(body));
        if (responseText == null) {
            return null;
        }
        JsonNode root = readTree(responseText);
        if (root == null) {
            return null;
        }
        String token = extractDataToken(root, "OAuth2 grant 接口");
        LogUtil.info(EfTokenExchangeUtil.class, "OAuth2 授权 token 换取{}", token == null ? "失败" : "成功");
        return token;
    }

    /**
     * 第二步：查询绑定列表，筛选终末地绑定信息
     *
     * <p>沿用原服务的筛选规则：只处理绑定账号数唯一的终末地应用，
     * 多账号绑定时无法确定目标角色，直接视为失败。</p>
     *
     * @param oauthToken 第一步换取的授权 token
     * @return 终末地绑定信息节点；未找到或绑定不唯一时返回 null
     */
    private static JsonNode findEndfieldBinding(String oauthToken) {
        String url = BINDING_LIST_API + "?token=" + EfPoolRecordUtil.encodeToken(oauthToken)
                + "&appCode=" + ENDFIELD_APP_CODE;
        String responseText = HttpClientUtil.get(url, buildHeaders());
        if (responseText == null) {
            return null;
        }
        JsonNode root = readTree(responseText);
        if (root == null) {
            return null;
        }
        int status = root.path("status").asInt(0);
        if (status != 0) {
            LogUtil.warn(EfTokenExchangeUtil.class, "绑定列表接口返回业务失败，status={}", status);
            return null;
        }
        for (JsonNode appInfo : root.path("data").path("list")) {
            if (ENDFIELD_APP_CODE.equals(appInfo.path("appCode").asText())) {
                JsonNode bindingList = appInfo.path("bindingList");
                if (bindingList.isArray() && bindingList.size() == 1) {
                    return bindingList.get(0);
                }
                LogUtil.warn(EfTokenExchangeUtil.class, "终末地绑定账号数异常，size={}",
                        bindingList.size());
                return null;
            }
        }
        return null;
    }

    /**
     * 从绑定信息中挑选默认角色
     *
     * <p>沿用原服务的挑选规则：仅一个角色时直接取该角色，
     * 多角色时取 isDefault 为 true 的默认角色。</p>
     *
     * @param binding 终末地绑定信息节点
     * @return 角色节点；角色列表为空或多角色且无默认角色时返回 null
     */
    private static JsonNode pickDefaultRole(JsonNode binding) {
        JsonNode roles = binding.path("roles");
        if (!roles.isArray() || roles.isEmpty()) {
            return null;
        }
        if (roles.size() == 1) {
            return roles.get(0);
        }
        for (JsonNode role : roles) {
            if (role.path("isDefault").asBoolean(false)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 第三步：用授权 token 与 uid 换取 u8Token
     *
     * @param oauthToken 第一步换取的授权 token
     * @param uid        终末地绑定账号 uid
     * @return u8Token；请求失败或响应缺少 token 时返回 null
     */
    private static String requestU8Token(String oauthToken, String uid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", oauthToken);
        body.put("uid", uid);
        String responseText = HttpClientUtil.postJson(U8_TOKEN_API, buildHeaders(),
                writeJson(body));
        if (responseText == null) {
            return null;
        }
        JsonNode root = readTree(responseText);
        if (root == null) {
            return null;
        }
        return extractDataToken(root, "u8Token 换取接口");
    }

    /**
     * 构造请求鹰角通行证接口所需的浏览器风格请求头
     *
     * <p>与原服务保持一致的伪装头；不设置 Connection（JDK HttpClient 受限头）
     * 与 Content-Type（由 {@link HttpClientUtil} 按请求类型自动设置）。</p>
     *
     * @return 请求头
     */
    private static Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0");
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,zh-TW;q=0.8,zh-HK;q=0.7,en-US;q=0.6,en;q=0.5");
        headers.put("Origin", "https://ak.hypergryph.com");
        headers.put("Referer", "https://ak.hypergryph.com/");
        headers.put("Sec-Fetch-Dest", "empty");
        headers.put("Sec-Fetch-Mode", "cors");
        return headers;
    }

    /**
     * 序列化请求体为 JSON 字符串，避免手工拼接引入转义问题
     *
     * @param body 请求体键值对
     * @return JSON 字符串；序列化失败时返回空对象 JSON
     */
    private static String writeJson(Map<String, Object> body) {
        try {
            return OBJECT_MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            LogUtil.error(EfTokenExchangeUtil.class, "请求体序列化失败", e);
            return "{}";
        }
    }

    /**
     * 解析响应文本为 JSON 树
     *
     * @param responseText 响应文本
     * @return JSON 根节点；解析失败时返回 null
     */
    private static JsonNode readTree(String responseText) {
        try {
            return OBJECT_MAPPER.readTree(responseText);
        } catch (JsonProcessingException e) {
            LogUtil.error(EfTokenExchangeUtil.class, "接口响应解析失败", e);
            return null;
        }
    }

    /**
     * 从标准响应结构（status/data）中提取 data.token 字段
     *
     * @param root      响应根节点
     * @param apiName   接口名称，用于日志定位
     * @return token；业务失败或字段缺失时返回 null
     */
    private static String extractDataToken(JsonNode root, String apiName) {
        int status = root.path("status").asInt(0);
        if (status != 0) {
            LogUtil.warn(EfTokenExchangeUtil.class, "{}返回业务失败，status={}", apiName, status);
            return null;
        }
        JsonNode tokenNode = root.path("data").path("token");
        if (tokenNode.isMissingNode() || tokenNode.asText().isBlank()) {
            LogUtil.warn(EfTokenExchangeUtil.class, "{}响应缺少 token 字段", apiName);
            return null;
        }
        return tokenNode.asText();
    }
}
