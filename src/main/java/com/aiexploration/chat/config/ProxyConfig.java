package com.aiexploration.chat.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.InetSocketAddress;
import java.net.Proxy;


@Configuration
public class ProxyConfig {

    @Bean
    public WebClientCustomizer customizer() {
        HttpClient httpClient = HttpClient.create()
                .proxy(proxy -> proxy
                        .type(ProxyProvider.Proxy.SOCKS5)
                        .host("localhost")
                        .port(1090)
                );
        return builder -> {
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        };
    }

    @Bean
    public RestClientCustomizer customizerRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1090));
        requestFactory.setProxy(proxy);
        return builder -> {
            builder.requestFactory(requestFactory);
//            builder.messageConverters(converters -> {
//                converters.add(new MappingJackson2HttpMessageConverter(
//                        new Jackson2ObjectMapperBuilder()
//                                .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL).build()
//                ));
//            });
        };
    }
}
