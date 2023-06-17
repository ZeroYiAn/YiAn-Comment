package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @description: 商铺类型操作实现类
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getList() {
        String key = CACHE_SHOPTYPE_KEY;
        //*****用String实现*******
        //1.从redis中查询店铺类型缓存
        String shopType = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPE_KEY);
        //2.判断是否为空
        if (StrUtil.isNotBlank(shopType)) {
            //3.存在，直接返回
            List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(shopTypes);
        }
        //4.不存在，从数据库中查询写入redis
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //5.不存在，返回错误
        if (shopTypes == null) {
            return Result.fail("分类不存在");
        }
        //6.存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY,JSONUtil.toJsonStr(shopTypes));
        //7.返回
        return Result.ok(shopTypes);
    }
}
