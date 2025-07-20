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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    void test() {
        String host = "127.0.0.1";
        int port = 16379;
        System.out.println("redis://%s:%d".formatted(host, port));
    }

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

}
