package com.microservices.user.service;

public interface TokenBlacklistService {

    void blacklist(String jti, long ttlSeconds);

    boolean isBlacklisted(String jti);
}
