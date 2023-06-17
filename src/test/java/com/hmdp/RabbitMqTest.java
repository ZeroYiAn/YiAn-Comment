package com.hmdp;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mq.producer.MqSender;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @description: RabbitMq发送消息测试
 * @author: ZeroYiAn
 * @time: 2023/5/25
 */
@Slf4j
@SpringBootTest
public class RabbitMqTest {
    @Resource
    private MqSender mqSender;
    @Test
    public void SendMsgTest(){
        mqSender.sendSeckillVoucherOrder(new VoucherOrder());
    }
}
