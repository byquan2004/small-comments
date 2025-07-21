package com.hmdp;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void createToken() {
        List<User> list = userService.list(new Page<>(1, 1000));
        Map<String, String> map = new HashMap<>();
        list.forEach(user -> {
            UserDTO dto = new UserDTO();
            BeanUtils.copyProperties(user, dto);
            String token = UUID.randomUUID(false).toString();
            String key = LOGIN_USER_KEY + token;
            String value = JSONUtil.toJsonStr(dto);
            map.put(key, value);
        });

        stringRedisTemplate.opsForValue().multiSet(map);
    }

    @Test
    void getTokens() {
        List<String> list = stringRedisTemplate.keys("login:token*").stream()
                .map(key -> key.split(":")[2])
                .toList();

        String csvFilePath = "/Users/yunquan/Downloads/tokens.csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFilePath))) {
            writer.write("token\n");
            list.forEach(token -> {
                try {
                    writer.write(token + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            System.out.println("Tokens 已保存到 CSV 文件: " + csvFilePath);
        } catch (IOException e) {
            System.err.println("写入文件时发生错误: " + e.getMessage());
        }
    }


    @Test
    void feedBoxTest() {
        // 获取当前时间戳
        long currentTimeMillis = System.currentTimeMillis();
//        System.out.println("Current Time Millis: " + currentTimeMillis);
//
//        // 查询 zset 中 score 在 [0, currentTimeMillis] 范围内的元素
//        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
//                .opsForZSet()
//                .reverseRangeByScoreWithScores("feed:1", 0, currentTimeMillis);
//
//        // 打印查询结果
//        if(typedTuples == null || typedTuples.isEmpty()) {
//            System.out.println("No elements found in the zset.");
//        }
//        System.out.println("Elements in the zset: "+ typedTuples);

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores("feed:1", 0, currentTimeMillis);
        System.out.println(typedTuples);
    }

    @Test
    void hyperLogLogTest() {
        /**
         * 单个hyperloglog 的内存占用大小用于 < 16kb 误差率在0.81%
         * uv(unique visitor) 唯一访问量或者独立访问量 如可适用于每天登录统计 date + ip
         * pv(page view) 页面访问量 如适用于统计页面的访问量点击量 timestamp
         */


        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_"+i;
            if(j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("uv", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("uv");
        System.out.println("count = "+count);
    }

}
