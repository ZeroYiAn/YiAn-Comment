package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @description: 博客服务接口
 * @author: ZeroYiAn
 * @time: 2023/5/13
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询热点博客
     * @param current
     * @return 查询结果
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id查询博客
     * @param id 博客id
     * @return 查询结果
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     * @param id 博客id
     * @return 点赞结果
     */
    Result likeBlog(Long id);

    /**
     * 查询点赞过该博客的用户
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     *查询关注的人发的博客
     * @param max  每页最多显示数
     * @param offset 偏移量
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);

    /**
     * 发布博客
     * @param blog 博客
     */
    Result saveBlog(Blog blog);


}
