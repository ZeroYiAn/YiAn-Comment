package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * @description: Redisson配置类
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        //单机(单服务器)模式，还可以配置集群模式，分片模式
        config.useSingleServer().setAddress("redis://192.168.200.154:6379").setPassword("780415");

        //创建RedissonClient对象
        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;

    }
}
