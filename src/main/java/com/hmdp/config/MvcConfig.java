package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
/**
 * @description: MVC配置类
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注册添加拦截器
     * @param registry  拦截器注册对象
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        //拦截器order值越小执行优先级越高
        //token刷新拦截器（目的是访问网页就进行刷新token有效期，有效期为30分钟，30分钟不操作网页登陆状态就失效）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                //拦截所有请求,先执行
                .addPathPatterns("/**").order(0);
        //登录拦截器（目的是只允许用户登录后才能访问到的一些界面）
        //后执行
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(//无需拦截器拦截（即无需登录）就可以执行的请求
                       "/user/code",
                       "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);

    }
}
