package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * 
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Autowired
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞博客
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryBlogByUserId(current, UserHolder.getUser().getId());
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    // BlogController
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        return blogService.queryBlogByUserId(current, id);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long blogId) {
        return blogService.queryBlogById(blogId);
    }

    @GetMapping("likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long blogId) {
        return blogService.queryBlogLikes(blogId);
    }
}
