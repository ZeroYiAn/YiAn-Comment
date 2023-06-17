package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @description: 代金券订单操作接口
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     * 使用Redis预减库存，Mq进行异步下单的优惠券秒杀优化
     * @param voucherId 优惠券id
     * @return
     */
    Result seckillVoucherWithMq(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

//    Result createVoucherOrder(Long voucherId);
}
