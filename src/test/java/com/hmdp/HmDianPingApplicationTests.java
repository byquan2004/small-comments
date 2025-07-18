package com.hmdp;

import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Test
    void test() {
        long l = redisIDWorker.nextId("order");
        System.out.println(l);
    }
}
