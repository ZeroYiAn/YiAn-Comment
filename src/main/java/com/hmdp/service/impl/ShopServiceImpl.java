package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @description: 商铺操作实现类
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    /**
     * 基于缓存查询商铺信息
     * @param id  商铺id
     * @return 查询结果
     */
    @Override
    public Result queryById(Long id) {
        //通过缓存空对象解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //利用缓存工具封装实现
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //利用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //利用逻辑过期解决缓存击穿
       // Shop shop = queryWithLogicExpire(id);
        if(shop==null){
            return  Result.fail("店铺不存在!");
        }
        return Result.ok(shop);


    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    private Shop queryWithLogicExpire(Long id) {
        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY+id;
        String shopJson= stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {//因为isNotBlank()方法对空字符串也判为false
            //3.不存在，直接返回null
            return null;
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //5.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1未过期，直接返回店铺信息
            return shop;
        }
        //5.2已过期，需要缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY+ id;
        boolean isLock = tryLock(lockKey);
        //6.2判断是否获取锁成功
        if(isLock){
            //6.3成功，开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //6.4返回过期的商铺信息
        return shop;

    }

    public Shop queryWithMutex(Long id){
        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY+id;
        String shopJson= stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        //因为isNotBlank()方法对空字符串也判为false
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }

        //所以为了应对缓存穿透，需要对空字符串进行进一步拦截
        if(shopJson!=null){
            //不是blank且不是null，则一定是空字符串
            //空字符串是我们为了防止缓存穿透自己缓存进去的
            //返回一个错误信息
            return null;
        }

        //4.缓存未命中，进行缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;

        //4.2判断是否获取成功
        Shop shop = null;
        try {
            //尝试通过setnx命令往redis插入lockKey，若插入成功返回true表示获取锁成功
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //4.3获取失败，有其他线程正在操作锁，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4 获取成功，
            //获取锁成功应该再次检测redis缓存是否存在，因为可能其他线程已经重建了缓存，做double Check
            //如果存在则无需重建缓存
            shopJson= stringRedisTemplate.opsForValue().get(key);
            //2.判断是否存在
            if (StrUtil.isNotBlank(shopJson)) {
                //3.存在，直接返回
                return JSONUtil.toBean(shopJson,Shop.class);
            }
            //不存在，表明是第一个进行缓存重建，开始重建缓存
            // 根据id查询数据库
            shop = getById(id);
            //测试：模拟延时
            // Thread.sleep(200);
            //5.数据库中不存在，返回错误
            if(shop==null){
                //应对缓存穿透：将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.存在，缓存写入redis 并设置过期时间
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unlock(lockKey);
        }
        //8.返回
        return shop;
    }


    public Shop queryWithPassThrough(Long id){
        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY+id;
        String shopJson= stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        //因为isNotBlank()方法对空字符串也判为false
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
        //所以为了应对缓存穿透，需要对空字符串进行进一步拦截
        if(shopJson!=null){
            //不是blank且不是null，则一定是空字符串
            //空字符串是我们为了防止缓存穿透自己缓存进去的
            //返回一个错误信息
            return null;
        }

        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        //5.数据库中不存在，返回错误
        if(shop==null){
            //应对缓存击穿：将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis 并设置过期时间
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        //7.返回
        return shop;
    }

    /**
     * 利用redis的setnx方法来表示获取锁，该方法含义是redis中如果没有这个key，则插入成功，返回1，在stringRedisTemplate中返回true
     * @param key  key
     * @return  返回锁获取是否成功
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


    public void saveShop2Redis(Long id,Long expireSeconds){

        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional //如果删除缓存操作出异常，更新数据库操作也要回滚
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
