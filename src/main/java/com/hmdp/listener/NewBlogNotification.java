package com.hmdp.listener;

import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.hmdp.utils.MQConstant.*;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Component
@RequiredArgsConstructor
public class NewBlogNotification {

    private static final Logger log = LoggerFactory.getLogger(NewBlogNotification.class);
    private final StringRedisTemplate stringRedisTemplate;

    private final IFollowService followService;

    /**
     * 新博客通知粉丝
     * @param msg {userId-作者ID,blogId-博客ID}
     */
    @RabbitListener(
            bindings = @QueueBinding(
                value = @Queue(name = NEW_BLOG_QUEUE),
                exchange = @Exchange(name = DEFAULT_DIRECT_EXC),
                key = {NEW_BLOG_BIND_KEY}
            )
    )
    public void notificationFans(Map<String, Object> msg) {

        log.info("新博客通知粉丝: {}", msg);

        // 查询作者粉丝 follow_user_id = userId
        List<Follow> fans = followService.lambdaQuery().eq(Follow::getFollowUserId, msg.get("userId")).list();
        // 推送粉丝收件箱 key -> feed:fansId, value -> blogId - timestamp
        String blogId = msg.get("blogId").toString();
        
        // 使用 ZAddOperations 批量添加 多次网络io
        ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
        fans.forEach(follow -> {
            String key = FEED_KEY + follow.getUserId();
            zSetOps.add(key, blogId, System.currentTimeMillis()); // 每个粉丝的收件箱独立存储
        });

        // 使用 Pipelined 批量添加 一次网络io
//        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
//            RedisZSetCommands zSetCommands = connection.zSetCommands();
//            for (Follow follow : fans) {
//                String key = FEED_KEY + follow.getUserId();
//                byte[] rawKey = stringRedisTemplate.getStringSerializer().serialize(key);
//                byte[] rawValue = stringRedisTemplate.getStringSerializer().serialize(String.valueOf(blogId));
//                zSetCommands.zAdd(rawKey, timestamp + 100L, rawValue);
//            }
//            return null;
//        });
    }
}
