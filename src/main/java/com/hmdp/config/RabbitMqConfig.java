package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.hmdp.utils.SystemConstants.SECKILL_EXCHANGE;
import static com.hmdp.utils.SystemConstants.SECKILL_QUEUE;


/**
 * @description: RabbitMQ配置类：采用主题模式Topic
 * @author: ZeroYiAn
 * @time: 2023/5/25
 */
@Configuration
public class RabbitMqConfig {


    @Bean
    public Queue queue(){
        return new Queue(SECKILL_QUEUE);
    }

    @Bean
    public TopicExchange topicExchange(){
        return new TopicExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Binding binding(){
        return BindingBuilder.bind(queue()).to(topicExchange()).with("seckill.#");
    }
}
