package com.orange.filter;

import com.orange.common.exception.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 干员批量保存接口的请求体大小限制过滤器
 *
 * <p>只对 {@code POST /user/ak-accounts/{akUid}/operators/save} 生效：用计数流包装请求体，
 * 读取累计超过预算时抛出 {@link PayloadTooLargeException}，由限定范围的异常处理器在应用内
 * 转换为 HTTP 200 + 业务码 10006，不依赖容器或网关拦截。</p>
 *
 * <p>该上限只是单次请求的资源保护，不是某个游戏账号累计干员条数的上限。</p>
 *
 * @author UserCenter
 */
@Component
public class SaveRequestBodySizeLimitFilter extends OncePerRequestFilter {

    /** 保存接口请求体上限（1 MiB），需按真实 JSON 采样验证后调整 */
    private static final long MAX_BODY_BYTES = 1024L * 1024;

    /** 受限路径前缀 */
    private static final String PATH_PREFIX = "/user/ak-accounts/";

    /** 受限路径后缀 */
    private static final String PATH_SUFFIX = "/operators/save";

    /** 解析应用内路径（自动去除 context-path） */
    private final UrlPathHelper urlPathHelper = UrlPathHelper.defaultInstance;

    /**
     * 判断当前请求是否需要参与请求体计数：仅保存接口的 POST 请求
     *
     * @param request 请求
     * @return true 表示跳过本过滤器
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && isSavePath(request));
    }

    /**
     * 用计数流包装请求后放行
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤器链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        filterChain.doFilter(new BodySizeLimitedRequest(request, MAX_BODY_BYTES), response);
    }

    /**
     * 判断请求路径是否为干员保存接口
     *
     * @param request 请求
     * @return 是否为保存接口路径
     */
    private boolean isSavePath(HttpServletRequest request) {
        String path = urlPathHelper.getPathWithinApplication(request);
        if (!path.startsWith(PATH_PREFIX) || !path.endsWith(PATH_SUFFIX)) {
            return false;
        }
        // 中间段必须是单个 akUid，不允许跨多级路径命中
        String akUidSegment = path.substring(PATH_PREFIX.length(), path.length() - PATH_SUFFIX.length());
        return !akUidSegment.isEmpty() && akUidSegment.indexOf('/') < 0;
    }

    /**
     * 请求包装器：把请求体读取替换为带字节计数的流
     */
    private static final class BodySizeLimitedRequest extends HttpServletRequestWrapper {

        /** 请求体字节预算 */
        private final long maxBytes;

        /**
         * 构造包装器
         *
         * @param request  原始请求
         * @param maxBytes 请求体字节预算
         */
        BodySizeLimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        /**
         * 返回计数输入流
         *
         * @return 计数输入流
         * @throws IOException IO 异常
         */
        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CountingServletInputStream(super.getInputStream(), maxBytes);
        }

        /**
         * 返回基于计数输入流的字符读取器
         *
         * @return 字符读取器
         * @throws IOException IO 异常
         */
        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    /**
     * 计数输入流：累计读取字节超过预算时立即抛出请求体超限异常
     */
    private static final class CountingServletInputStream extends ServletInputStream {

        /** 被包装的原始流 */
        private final ServletInputStream delegate;

        /** 请求体字节预算 */
        private final long maxBytes;

        /** 已读取字节数 */
        private long readBytes;

        /**
         * 构造计数流
         *
         * @param delegate 原始输入流
         * @param maxBytes 请求体字节预算
         */
        CountingServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        /**
         * 是否已读完
         *
         * @return 是否读完
         */
        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        /**
         * 是否可无阻塞读取
         *
         * @return 是否就绪
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 设置非阻塞读取监听器
         *
         * @param readListener 读取监听器
         */
        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        /**
         * 读取单个字节并计数
         *
         * @return 字节值，流结束返回 -1
         * @throws IOException IO 异常
         */
        @Override
        public int read() throws IOException {
            int value = delegate.read();
            count(value < 0 ? 0 : 1);
            return value;
        }

        /**
         * 批量读取字节并计数
         *
         * @param buffer 缓冲区
         * @param offset 起始偏移
         * @param length 期望读取长度
         * @return 实际读取字节数，流结束返回 -1
         * @throws IOException IO 异常
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            count(Math.max(read, 0));
            return read;
        }

        /**
         * 关闭底层流
         *
         * @throws IOException IO 异常
         */
        @Override
        public void close() throws IOException {
            delegate.close();
        }

        /**
         * 累计已读字节，超出预算立即拒绝，避免继续把超大请求体读进内存
         *
         * @param bytes 本次读取的字节数
         */
        private void count(int bytes) {
            if (bytes <= 0) {
                return;
            }
            readBytes += bytes;
            if (readBytes > maxBytes) {
                throw new PayloadTooLargeException("请求体超过大小限制（" + maxBytes + "字节）");
            }
        }
    }
}
