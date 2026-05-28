package com.omni.business.feign;

import com.omni.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client example - calls other microservices by name.
 * Replace with actual service interfaces as needed.
 */
@FeignClient(name = "remote-service", path = "/api", fallbackFactory = RemoteServiceFallbackFactory.class)
public interface RemoteServiceFeignClient {

    @GetMapping("/data/{id}")
    R<String> getRemoteData(@PathVariable("id") Long id);
}
