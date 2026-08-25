package com.dochub.workbench.docgen.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 文枢 DocHub 文档生成线程池配置。
 */
@Configuration
@EnableConfigurationProperties(DocGenProperties.class)
public class DocGenConfiguration {

    @Bean("docGenExecutorService")
    public ExecutorService docGenExecutorService(DocGenProperties properties) {
        int threads = Math.max(1, properties.getExecutorThreads());
        return new ThreadPoolExecutor(
            threads, threads,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactoryBuilder().setNamePrefix("dochub-docgen-").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
