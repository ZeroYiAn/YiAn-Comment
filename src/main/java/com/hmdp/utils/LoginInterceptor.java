package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
/**
 * @description: 登录拦截器
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 进入controller之前进行用户登录校验
     * @param request 登录请求
     * @param response  登录响应
     * @param handler  处理器
     * @return 验证结果
     * @throws Exception 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断是否需要拦截(ThreadLocal中是否有用户)
        if(UserHolder.getUser()==null){
            //没有，需要拦截，设置状态码401：unauthorized未授权
            response.setStatus(401);
            //校验失败，进行拦截
            return false;
        }
        //有用户，则放行
        return true;
    }

}
