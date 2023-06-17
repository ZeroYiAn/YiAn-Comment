package com.hmdp.mq.producer;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Arrays;
import java.util.List;

import static com.hmdp.utils.SystemConstants.SECKILL_EXCHANGE;

/**
 * @description:
 * @author: ZeroYiAn
 * @time: 2023/5/25
 */
@Slf4j
@Service
public class MqSender {
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送秒杀优惠券订单
     * @param voucherOrder 优惠券订单
     */
    public void sendSeckillVoucherOrder(VoucherOrder voucherOrder){
        String objJson = JSONUtil.toJsonStr(voucherOrder);


        log.info("发送MQ消息(优惠券订单):{}",objJson);
        rabbitTemplate.convertAndSend(SECKILL_EXCHANGE,"seckill.message",objJson);
    }

//    public void send(Object msg){
//        log.info("发送消息:"+msg);
//        rabbitTemplate.convertAndSend("queue",msg);
//    }
}
