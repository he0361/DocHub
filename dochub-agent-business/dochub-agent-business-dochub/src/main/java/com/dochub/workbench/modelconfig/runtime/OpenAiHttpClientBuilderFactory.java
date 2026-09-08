package com.dochub.workbench.modelconfig.runtime;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/** Creates the synchronous and streaming HTTP builders for one candidate timeout. */
public interface OpenAiHttpClientBuilderFactory {

    RestClient.Builder restClientBuilder(Duration timeout);

    WebClient.Builder webClientBuilder(Duration timeout);

    static OpenAiHttpClientBuilderFactory defaults() {
        return new DefaultOpenAiHttpClientBuilderFactory();
    }

    static OpenAiHttpClientBuilderFactory vllm() {
        return new VllmOpenAiHttpClientBuilderFactory();
    }
}

final class DefaultOpenAiHttpClientBuilderFactory implements OpenAiHttpClientBuilderFactory {

    @Override
    public RestClient.Builder restClientBuilder(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public WebClient.Builder webClientBuilder(Duration timeout) {
        JdkClientHttpConnector connector = new JdkClientHttpConnector(
            java.net.http.HttpClient.newBuilder().connectTimeout(timeout).build());
        connector.setReadTimeout(timeout);
        return WebClient.builder().clientConnector(connector);
    }
}

final class VllmOpenAiHttpClientBuilderFactory implements OpenAiHttpClientBuilderFactory {

    @Override
    public RestClient.Builder restClientBuilder(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public WebClient.Builder webClientBuilder(Duration timeout) {
        HttpClient client = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
            .responseTimeout(timeout);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client));
    }
}
