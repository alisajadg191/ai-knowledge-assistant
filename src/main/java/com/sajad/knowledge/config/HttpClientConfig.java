package com.sajad.knowledge.config;

import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {
    @Bean @Scope("prototype")
    RestClient.Builder restClientBuilder() {
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory);
    }
    @Bean @Scope("prototype")
    WebClient.Builder webClientBuilder() {
        var client = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client));
    }
}
