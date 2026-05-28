package com.omni.business.feign;

import com.omni.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for RemoteServiceFeignClient.
 * Provides degraded responses when the remote service is unavailable.
 */
@Slf4j
@Component
public class RemoteServiceFallbackFactory implements FallbackFactory<RemoteServiceFeignClient> {

    @Override
    public RemoteServiceFeignClient create(Throwable cause) {
        log.error("Remote service call failed: {}", cause.getMessage());
        return new RemoteServiceFeignClient() {
            @Override
            public R<String> getRemoteData(Long id) {
                return R.fail("Remote service unavailable, fallback response");
            }
        };
    }
}
