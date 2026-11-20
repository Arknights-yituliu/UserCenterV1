package com.orange.interceptor;

import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.mapper.UserInfoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 在凭证校验之后检查账户存在性，封住与注销并发产生的残留凭证。 */
@Configuration
public class AccountLifecycleWebConfig implements WebMvcConfigurer {

    private final UserInfoMapper userMapper;

    public AccountLifecycleWebConfig(UserInfoMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Long uid = UserContext.getUid();
                if (uid != null && userMapper.selectById(uid) == null) {
                    throw new BusinessException(ResultCode.NOT_LOGIN);
                }
                return true;
            }
        }).addPathPatterns("/user/**", "/auth/logout", "/oauth2/**", "/pat/**").order(100);
    }
}
