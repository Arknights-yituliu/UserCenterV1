package com.orange.common.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 外接 HTTP 调用工具类：仅用于访问第三方公开接口（如森空岛 / 鹰角通行证）
 *
 * <p>基于 JDK 17 内置的 {@link HttpClient}，不额外引入依赖。约定：</p>
 * <ul>
 *   <li>请求成功（无论状态码）返回响应体原文，由调用方解析业务状态字段；</li>
 *   <li>网络异常或超时返回 null，并记录日志；</li>
 *   <li>非 2xx 响应只记录状态码与地址，不打印响应体，避免上游回显的凭证进入日志。</li>
 * </ul>
 *
 * @author UserCenter
 */
public final class HttpClientUtil {

    /** 建立连接超时时间 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 单次请求总超时时间 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** 共享客户端：HttpClient 线程安全且可复用，避免每次请求重复创建连接池 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private HttpClientUtil() {
    }

    /**
     * 发送 JSON POST 请求
     *
     * @param url      请求地址
     * @param headers  请求头，可为空
     * @param jsonBody JSON 请求体
     * @return 响应体原文；网络异常时返回 null
     */
    public static String postJson(String url, Map<String, String> headers, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * 发送表单 POST 请求（application/x-www-form-urlencoded）
     *
     * @param url        请求地址
     * @param formParams 表单参数，可为空
     * @param headers    请求头，可为空
     * @return 响应体原文；网络异常时返回 null
     */
    public static String postForm(String url, Map<String, String> formParams, Map<String, String> headers) {
        StringBuilder body = new StringBuilder();
        if (formParams != null) {
            for (Map.Entry<String, String> entry : formParams.entrySet()) {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                                StandardCharsets.UTF_8));
            }
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * 发送 GET 请求
     *
     * @param url     请求地址（含 query 参数）
     * @param headers 请求头，可为空
     * @return 响应体原文；网络异常时返回 null
     */
    public static String get(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET();
        applyHeaders(builder, headers);
        return execute(builder.build());
    }

    /**
     * 写入自定义请求头
     *
     * @param builder 请求构建器
     * @param headers 请求头，可为空
     */
    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach(builder::header);
    }

    /**
     * 执行请求并返回响应体
     *
     * @param request 请求
     * @return 响应体原文；网络异常或线程中断时返回 null
     */
    private static String execute(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                LogUtil.warn(HttpClientUtil.class, "第三方请求返回非 2xx，url={}，status={}", request.uri(), status);
            }
            return response.body();
        } catch (IOException e) {
            LogUtil.error(HttpClientUtil.class, "第三方请求失败，url=" + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LogUtil.error(HttpClientUtil.class, "第三方请求被中断，url=" + request.uri(), e);
        }
        return null;
    }
}
