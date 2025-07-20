package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.FOLLOW_USER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, IUserService userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if(userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        if(isFollow) {
            // 关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId); // 关注用户
            follow.setUserId(userId); // 当前登录用户
            boolean isSuccess = saveOrUpdate(follow);
            if(isSuccess) {
                // 查询关注的时候可能显示关注顺序、分页查询所以选择sortedSet
                stringRedisTemplate.opsForZSet().add(FOLLOW_USER_KEY + userId,
                        followUserId.toString(), System.currentTimeMillis());
            }
        } else {
            // 取关
            boolean isSuccess = remove(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, userId)
                            .eq(Follow::getFollowUserId, followUserId)
            );
            if(isSuccess) {
                // 查询关注的时候可能显示关注顺序、分页查询所以选择sortedSet
                stringRedisTemplate.opsForZSet().remove(FOLLOW_USER_KEY + userId, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = lambdaQuery().eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .count();
        if(count > 0L) {
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    @Override
    public Result followCommons(Long userId) {
        Long me = UserHolder.getUser().getId();
        Set<String> followCommons = stringRedisTemplate.opsForZSet()
                .intersect(FOLLOW_USER_KEY + userId, FOLLOW_USER_KEY + me);

        if(followCommons == null || followCommons.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = followCommons.stream().map(Long::valueOf).toList();
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);

    }
}
