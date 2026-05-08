package com.microservices.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application Context Load Test")
class UserServiceApplicationTests {

    // Redis is not available in test; mock the template so all Redis-backed beans start cleanly
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("Spring context loads successfully with H2 and mocked Redis")
    void contextLoads() {
        assertTrue(true, "Application context loaded without errors");
    }
}
