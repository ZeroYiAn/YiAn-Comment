package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @description: 关注服务接口
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注、取关功能
     * @param followUserId  关联的用户id
     * @param isFollow  关注状态
     * @return 结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断是否关注
     * @param followUserId  关联的用户id
     * @return   结果
     */
    Result isFollow(Long followUserId);

    /**
     * 查询共同关注用户列表
     * @param id 要查询用户的id
     * @return 查询结果
     */
    Result followCommons(Long id);
}
