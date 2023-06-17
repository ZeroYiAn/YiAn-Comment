package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 服务实现类:继承MyBatisPlus提供的ServiceImpl类，可以帮助实现单表的增删改查
 * @author  ZeroYiAn
 * @since  2021-12-22
 */
@Slf4j
@Service
/**
 *
 */
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3.符合，生成校验码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis, 过期时间2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        /*
        具体实现需要借助阿里云等第三方平台
         */
        log.debug("验证码发送成功，验证码：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号（防止用户在发送收到验证码后使用不正确的手机号）
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // TODO 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if(cacheCode==null||!cacheCode.equals(code)){
            //4.不一致，报错
            return Result.fail("验证码错误");
        }
        //5.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        //6.判断用户是否存在
        if(user==null){
            //7、不存在，注册创建新用户并保存
            user = createUserWithPhone(phone);
        }
        //保存用户信息到redis中
        //7.1随机生成token，作为登录令牌(Key)，因为如果用手机号作为key的话
        //把这样的敏感数据存储到redis中并且从页面中带过来不太合适
        String token = UUID.randomUUID().toString(true);
        //7.2将User对象转为HashMap存储
        //脱敏操作：将User对象转成不含敏感信息的UserDTO对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        HashMap<String,Object>userMap = new HashMap<>();
//        userMap.put("id",userDTO.getId());
//        userMap.put("nickName",userDTO.getNickName());
//        userMap.put("icon",userDTO.getIcon());
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                //做数据拷贝时的选项
                CopyOptions.create()
                        //忽略空值
                        .setIgnoreNullValue(true)
                        //对字段值的修改器：这里把所有字段的值都存储为String类型
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        //7.3存储
        String tokenKey = LOGIN_USER_KEY+token;
        //把键值对K-V存入redis，这里的Value存储的是hash结构
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //7.4 设置token有效期:30分钟
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.返回token给客户端
        return Result.ok(token);
    }

    /**
     * 用户注册
     * @param phone  手机号
     * @return  用户对象
     */
    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }
}
