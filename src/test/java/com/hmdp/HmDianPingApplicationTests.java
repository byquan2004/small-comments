package com.hmdp;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {


    @Test
    void test() {
        System.out.println(RandomUtil.randomString(6));
        System.out.println(UUID.randomUUID(false));
    }
}
