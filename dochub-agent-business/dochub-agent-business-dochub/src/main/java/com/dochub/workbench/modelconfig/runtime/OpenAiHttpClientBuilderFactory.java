package com.dochub.workbench.modelconfig.runtime;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Creates the synchronous and streaming HTTP builders for one candidate timeout. */
interface OpenAiHttpClientBuilderFactory {

    RestClient.Builder restClientBuilder(Duration timeout);

    WebClient.Builder webClientBuilder(Duration timeout);

    static OpenAiHttpClientBuilderFactory defaults() {
        return new DefaultOpenAiHttpClientBuilderFactory();
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
            HttpClient.newBuilder().connectTimeout(timeout).build());
        connector.setReadTimeout(timeout);
        return WebClient.builder().clientConnector(connector);
    }
}
