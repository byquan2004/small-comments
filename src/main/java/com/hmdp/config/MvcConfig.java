package com.hmdp.config;

import com.hmdp.interceptor.LoginUserInterceptor;
import com.hmdp.interceptor.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注册拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate));
        registry.addInterceptor(new LoginUserInterceptor())
                // 排除拦截
                .excludePathPatterns(
                    "/shop/**",
                    "/voucher/**",
                    "/shop-type/**",
                    "/upload/**",
                    "/blog/hot",
                    "/user/code",
                    "/user/login"
                );
    }
}
