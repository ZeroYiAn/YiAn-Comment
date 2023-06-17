package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
/**
 * @description: 刷新Token有效期的拦截器，这个拦截器放行所有页面，不做拦截，只刷新token
 * 即只要访问了任意界面就可以刷新token有效期
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    /**
     * 这里只能利用构造函数完成注入（看谁用了它，即MvcConfig中用到了拦截器）
     * 因为LoginInterceptor类的对象是我们自己手动new出来的，不是由spring创建的
     */
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 进入controller之前进行用户登录校验
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //直接放行
            return true;
        }
        //2.基于token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().
                entries(RedisConstants.LOGIN_USER_KEY+token);
        //3.判断用户是否存在
        if(userMap.isEmpty()){
            //4.不存在，拦截, 返回401状态码 未授权
            response.setStatus(401);
            return false;
        }
        //5.将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        //7.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.放行
        return true;
    }

    /**
     * 用户业务执行完毕，销毁用户信息
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
