package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static com.hmdp.utils.MQConstant.DEFAULT_DIRECT_EXC;
import static com.hmdp.utils.MQConstant.NEW_BLOG_BIND_KEY;
import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */
@Service
@RequiredArgsConstructor
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        UserDTO user = UserHolder.getUser();
        String userId;
        if(user != null) {
            userId = user.getId().toString();
        } else {
            userId = null;
        }
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        if(page.getTotal() < 1 || page.getRecords().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 获取当前页数据
        List<Blog> blogs = page.getRecords();
        if (userId == null) {
            blogs.forEach(this::getUserByBlog);
        }else {
            blogs.forEach(blog ->{
                this.blogLiked(blog,userId);
                this.getUserByBlog(blog);
            });
        }

        return Result.ok(blogs);
    }

    private void getUserByBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long blogId) {
        Blog blog = getById(blogId);
        if(blog == null || ObjectUtils.isEmpty(blog)) {
            return Result.fail("博客不存在");
        }
        String userId = UserHolder.getUser().getId().toString();
        String key = BLOG_LIKED_KEY + blogId;
        blogLiked(blog,userId);
        if(blog.getIsLike()) {
            stringRedisTemplate.opsForZSet().remove(key,userId);
            lambdaUpdate().eq(Blog::getId,blogId).setSql("liked = liked - 1").update();
        }else {
            stringRedisTemplate.opsForZSet().add(key,userId,System.currentTimeMillis());
            lambdaUpdate().eq(Blog::getId,blogId).setSql("liked = liked + 1").update();
        }
        blog.setIsLike(!blog.getIsLike());
        return Result.ok();
    }

    private void blogLiked(Blog blog, String userId) {
        Long blogId = blog.getId();
        String key = BLOG_LIKED_KEY + blogId;
        Double theUserScore = stringRedisTemplate.opsForZSet().score(key, userId);
        blog.setIsLike(theUserScore != null);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long userId) {
        Page<Blog> page = lambdaQuery().eq(Blog::getUserId, userId)
                .orderByDesc(Blog::getLiked)
                .orderByDesc(Blog::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        blogs.forEach(blog ->{
            this.blogLiked(blog,userId.toString());
            this.getUserByBlog(blog);
        });
        return Result.ok(blogs);
    }

    @Override
    public Result queryBlogById(Long blogId) {
        Blog blog = getById(blogId);
        if(blog == null) {
            return Result.fail("博客不存在");
        }
        this.getUserByBlog(blog);
        this.blogLiked(blog,UserHolder.getUser().getId().toString());
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long blogId) {
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + blogId, 0, 5);
        if(top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> list = userService.lambdaQuery().in(User::getId, top5)
                .last("ORDER BY FIELD(id,%s)".formatted(String.join(",", top5)))
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(list);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess) {
            return Result.fail("新增博客笔记失败！");
        }
        // mq异步推送粉丝收件箱
        Map<String, Object> map = new HashMap<>();
        map.put("blogId",blog.getId());
        map.put("userId",user.getId());
        rabbitTemplate.convertAndSend(DEFAULT_DIRECT_EXC,NEW_BLOG_BIND_KEY,map);
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询登录用户关注博主发布的博客
     * @param lastScore
     * @param offset
     * @return
     */
    @Override
    public Result ofFollow(Long lastScore, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId; // 获取收件箱

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                // 从为 “key” 集合中 查询0 - lastScore 的成员 跳过 offset 个成员 查询5个
                .reverseRangeByScoreWithScores(key, 0, lastScore, offset, 5);

        if(typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        long minTime = 0; // 获取最小时间戳
        int os = 0; // 偏移量
        List<Long> ids = new ArrayList<>(typedTuples.size());
        // 主要作用就是记录最小值和偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(typedTuple.getValue())); // 收集博客id
            long time = typedTuple.getScore().longValue();
            if(time == minTime) {
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }

        List<Blog> blogs = lambdaQuery().in(Blog::getId, ids)
                .last("ORDER BY FIELD(id,%s)".formatted(StrUtil.join(",", ids)))
                .list();

        blogs.forEach(blog -> {
            this.getUserByBlog(blog);
            this.blogLiked(blog,userId.toString());
        });

        ScrollResult res = new ScrollResult();
        res.setList(blogs);
        res.setOffset(os);
        res.setMinTime(minTime);

        return Result.ok(res);
    }
}
