package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
/**
 * @description: 简单Redis分布式锁工具类
 * @author: ZeroYiAn
 * @time: 2023/5/16
 */
public class SimpleRedisLock implements Ilock{
    /**
     * 锁的名称，不同业务的锁名不同
     */
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock：";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    /**静态域按照声明顺序执行，这里先执行静态变量再执行静态代码块*/
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁，利用一行命令同时设置setNx 和 EX(超时时间)，实现原子性
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);

       // return success; //Boolean转boolean有自动拆箱风险
        return Boolean.TRUE.equals(success);
    }

    /**
     * 利用lua脚本使拿锁、比锁、删锁动作具有原子性
     */
    @Override
    public void unlock(){
        //调用Lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                //转换成单元素的集合
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断标识是否一致
//        if(threadId.equals(id)){
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//        //否则锁由过期时间自动释放，不进行操作
//    }
}
