package com.microservices.user.service;

import com.microservices.user.service.impl.TokenBlacklistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenBlacklistServiceImpl Unit Tests")
class TokenBlacklistServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistServiceImpl tokenBlacklistService;

    private static final String KEY_PREFIX = "auth:blacklist:";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("blacklist: writes the jti key to Redis with the correct TTL")
    void blacklist_writesKeyToRedisWithTtl() {
        String jti = "abc-123-jti";
        long ttl = 900L;

        tokenBlacklistService.blacklist(jti, ttl);

        verify(valueOperations).set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttl));
    }

    @Test
    @DisplayName("blacklist: uses the correct key prefix")
    void blacklist_usesCorrectKeyPrefix() {
        String jti = "some-jti";
        tokenBlacklistService.blacklist(jti, 300L);
        verify(valueOperations).set(eq(KEY_PREFIX + jti), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("isBlacklisted: returns true when the Redis key exists")
    void isBlacklisted_returnsTrue_whenKeyExists() {
        String jti = "revoked-jti";
        when(redisTemplate.hasKey(KEY_PREFIX + jti)).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(jti));
    }

    @Test
    @DisplayName("isBlacklisted: returns false when the Redis key does not exist")
    void isBlacklisted_returnsFalse_whenKeyAbsent() {
        String jti = "valid-jti";
        when(redisTemplate.hasKey(KEY_PREFIX + jti)).thenReturn(false);

        assertFalse(tokenBlacklistService.isBlacklisted(jti));
    }

    @Test
    @DisplayName("isBlacklisted: returns false when Redis returns null (key absent)")
    void isBlacklisted_returnsFalse_whenRedisReturnsNull() {
        String jti = "any-jti";
        when(redisTemplate.hasKey(KEY_PREFIX + jti)).thenReturn(null);

        assertFalse(tokenBlacklistService.isBlacklisted(jti));
    }

    @Test
    @DisplayName("isBlacklisted: queries Redis with the full prefixed key")
    void isBlacklisted_queriesWithCorrectKey() {
        String jti = "check-prefix-jti";
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        tokenBlacklistService.isBlacklisted(jti);

        verify(redisTemplate).hasKey(KEY_PREFIX + jti);
    }
}
