package com.omni.gateway.config;

import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Netty 服务器自定义配置。
 * <p>
 * 增大 Netty HTTP 解码器的最大 Header 尺寸至 64KB，
 * 以支持携带大量 scope 的大型 JWT Token。
 * </p>
 */
@Configuration
public class NettyServerConfig {

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer() {
        return factory -> factory.addServerCustomizers(builder ->
                builder.httpRequestDecoder(spec -> spec.maxHeaderSize(65536))
        );
    }
}
