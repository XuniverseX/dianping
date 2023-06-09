package com.dianping.config;

import com.dianping.utils.LoginInterceptor;
import com.dianping.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/shop/**",
                "/shop-type/**",
                "/voucher/**",
                "/upload/**",
                "/blog/hot",
                "/user/login",
                "/user/code"
        ).order(1);
    }
}
