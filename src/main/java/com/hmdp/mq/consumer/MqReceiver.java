package com.hmdp.mq.consumer;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.SystemConstants.SECKILL_QUEUE;

/**
 * @description:
 * @author: ZeroYiAn
 * @time: 2023/5/25
 */
@Slf4j
@Service
public class MqReceiver {
    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(queues = SECKILL_QUEUE)
    public void receiveSeckillVoucherOrder(String message){
        VoucherOrder voucherOrder = JSONUtil.toBean(message, VoucherOrder.class);
        log.info("接受MQ消息：{}",message);
      //  Long userId = voucherOrder.getUserId();
      //  Long voucherId = voucherOrder.getVoucherId();
        //创建优惠券订单
        voucherOrderService.createVoucherOrder(voucherOrder);

    }
//    @RabbitListener(queues = "queue")
//    public void receive(Object msg){
//        log.info("接收消息："+msg);
//    }
}
