package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @description: 商铺类型操作接口
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public interface IShopTypeService extends IService<ShopType> {
    /**
     * 获取该类型商铺列表
     * @return  获取是否成功
     */
    Result getList();
}
