package com.microservices.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@Slf4j
public class BranchValidationService {

    private final RestTemplate loadBalancedRestTemplate;

    @Value("${branch-service.url:http://branch-service}")
    private String branchServiceBaseUrl;

    public BranchValidationService(@Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
    }

    /**
     * Returns true if GET branch-service/api/v1/branches/{id} returns 2xx.
     */
    public boolean branchExists(UUID branchId) {
        if (branchId == null) {
            return false;
        }
        String url = branchServiceBaseUrl + "/api/v1/branches/" + branchId;
        try {
            ResponseEntity<Object> response = loadBalancedRestTemplate.getForEntity(url, Object.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.debug("Branch {} not found or branch-service error: {}", branchId, e.getMessage());
            return false;
        }
    }
}
