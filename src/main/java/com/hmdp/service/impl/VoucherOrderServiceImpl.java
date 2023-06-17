package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.producer.MqSender;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private MqSender mqSender;


    /***************************************************************************************************/
    @Override
    public Result seckillVoucherWithMq(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId2 = redisIdWorker.nextId("order");
        //        //1.向数据库中查询优惠券是否在有效时间内,不行，速度太慢，可以把优惠券(有效时间)缓存到redis中,
        //再把这个判断过程写到lua脚本
        //SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        ////2.判断秒杀是否已经开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始!");
//        }
//        //3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束!");
//        }
        //1.执行lua脚本
        //调用Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                //转换成单元素的集合
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId2));

        //2.判断结果是否是0
        int res = result.intValue();
        if (res != 0) {
            //2.1不为0，代表没有购买资格
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }

        //2.2为0，有购买资格，把下单信息保存到队列

        //0 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //0.1创建订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //0.2用户id
        voucherOrder.setUserId(userId);
        //0.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //TODO 把订单信息放入消息队列
        mqSender.sendSeckillVoucherOrder(voucherOrder);


        //4.返回订单id
        return Result.ok(orderId);

    }


    /***************************************************************************************************/

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 通过@PostConstruct注解，VoucherOrderServiceImpl对象一旦初始化完毕就会执行该方法，从而执行run方法
     */
//    @PostConstruct
//    private void init() {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }

    /**
     * 通过阻塞队列执行异步下单
     */
//    private BlockingQueue<VoucherOrder>orderTasks = new ArrayBlockingQueue<>(1024*1024);
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    //1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.获取到订单，创建订单
//                    handleVoucherOrder(voucherOrder);
//
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }


    private class VoucherOrderHandler implements Runnable {
        private static final String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 0.初始化stream
                    initStream();
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1如果获取失败，说明此时没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息中的订单信息，这里list的size是1
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    //3.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4. 消息队列的ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName, "g1", record.getId()
                    );

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        public void initStream() {
            Boolean exists = stringRedisTemplate.hasKey(queueName);
            if (BooleanUtil.isFalse(exists)) {
                log.info("stream不存在，开始创建stream");
                // 不存在，需要创建
                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
                log.info("stream和group创建完毕");
                return;
            }
            // stream存在，判断group是否存在
            StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(queueName);
            if (groups.isEmpty()) {
                log.info("group不存在，开始创建group");
                // group不存在，创建group
                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
                log.info("group创建完毕");
            }
        }

        private void handlePendingList() {

            while (true) {
                try {
                    //1.获取Pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.order 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1如果获取失败，说明pending-list没有消息，结束循环
                        break;
                    }
                    //3.解析消息中的订单信息，这里list的size是1
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    //3.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4. 消息队列的ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName, "g1", record.getId()
                    );

                } catch (Exception e) {
                    log.error("处理pending-list异常", e);
                }
            }
        }
    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        //基于Redisson实现分布式锁，理论上这里不会出现并发异常，加锁只是为了兜底
        RLock lock = redissonClient.getLock("lock:order" + userId);
        //获取锁,有默认参数
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //获取锁失败，返回错误或重试
            log.error("不允许重复下单");
        }
        try {
            //利用代理对象调用函数，防止事务失效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    //作为全局变量(成员变量)供handleVoucherOrder()函数使用
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //        //1.向数据库中查询优惠券,不行，速度太慢，可以把优惠券(有效时间)缓存到redis中,
        //再把这个判断过程写到lua脚本
        //SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        ////2.判断秒杀是否已经开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始!");
//        }
//        //3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束!");
//        }
        //1.执行lua脚本
        //调用Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                //转换成单元素的集合
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        //2.判断结果是否是0
        int res = result.intValue();
        if (res != 0) {
            //2.1不为0，代表没有购买资格
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }


        //获取代理对象
        //获得IVoucherOrderService接口的代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //返回订单id
        return Result.ok(orderId);
    }


//    public Result seckillVoucher(Long voucherId) {
//        //获取用户
//        Long userId = UserHolder.getUser().getId();
//        //        //1.向数据库中查询优惠券,不行，速度太慢，可以把优惠券(有效时间)缓存到redis中,
//        //再把这个判断过程写到lua脚本
//       //SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        ////2.判断秒杀是否已经开始
////        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
////            //尚未开始
////            return Result.fail("秒杀尚未开始!");
////        }
////        //3.判断秒杀是否已经结束
////        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
////            return Result.fail("秒杀已经结束!");
////        }
//        //1.执行lua脚本
//        //调用Lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                //转换成单元素的集合
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString());
//        //2.判断结果是否是0
//        int res = result.intValue();
//        if(res!=0){
//            //2.1不为0，代表没有购买资格
//            return Result.fail(res==1?"库存不足":"不能重复下单");
//        }
//
//        //2.2为0，有购买资格，把下单信息保存到阻塞队列
//
//        //0 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //0.1创建订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //0.2用户id
//        voucherOrder.setUserId(userId);
//        //0.3代金券id
//        voucherOrder.setVoucherId(voucherId);
//        //0.4 把订单信息放入阻塞队列
//        orderTasks.add(voucherOrder);
//
//        //3.获取代理对象
//        //获得IVoucherOrderService接口的代理对象
//         proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        //4.返回订单id
//        return Result.ok(orderId);
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 5.1.查询订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
//        // 5.2.判断是否存在
//        if (count > 0) {
//            // 用户已经购买过了
//            log.error("用户已经购买过了");
//            return ;
//        }

        // 6.从数据库中扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足");
            return;
        }
        save(voucherOrder);

    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        //1.查询优惠券
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否已经开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始!");
//        }
//        //3.判断秒杀是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束!");
//        }
//        //4。判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //自己创建锁对象(基于redis的分布式锁)
//        //分布式体现在：就算同一个用户同一时间发送多个请求，也只能去同一个redis获取锁，锁对象是同一个，只能获取一次
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//
//       //基于Redisson实现分布式锁
//        RLock lock = redissonClient.getLock("lock:order" + userId);
//        //获取锁,有默认参数
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            //获取锁失败，返回错误或重试
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            //获得IVoucherOrderService接口的代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //利用代理对象调用函数，防止事务失效
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//
//
//    }

    /**
     * 该方法内同时对两张表进行操作，加上事务保证一致性
     * @param voucherId  订单id
     * @return 创建结果，订单id
     */
//    @Transactional
//    @Override
//    public Result createVoucherOrder(Long voucherId) {
//        //一人一单
//        Long UserId = UserHolder.getUser().getId();
//        //查询订单
//        int count = query().eq("user_id", UserId).eq("voucher_id", voucherId).count();
//        //判断是否已经存在
//        if (count > 0) {
//            //用户已经购买过
//            return Result.fail("用户已购买过一次!");
//        }
//        //5.扣减库存(乐观锁，对于库存比较特殊，只需要判断是否大于0即可，不用过于关注库存是否发生变化)
//        boolean success = iSeckillVoucherService.update()
//                .setSql("stock = stock -1")//set stock = stock -1
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0)//where id=? and stock >0  (防止超卖)
//                .update();
//        if (!success) {
//            //扣减失败
//            return Result.fail("库存不足！");
//        }
//        //6.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1订单id
//        long orderId = redisIdWorker.nextId("order");//创建一个id
//        voucherOrder.setId(orderId);
//        //6.2用户id
//        Long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//        //6.3代金券id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        //7.返回订单id
//        return Result.ok(orderId);
//
//    }


}
