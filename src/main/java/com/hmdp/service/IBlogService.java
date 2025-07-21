package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * 
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBlogByUserId(Integer current, Long userId);

    Result queryBlogById(Long blogId);

    Result queryBlogLikes(Long blogId);

    Result saveBlog(Blog blog);

    Result ofFollow(Long lastId, Integer offset);
}
