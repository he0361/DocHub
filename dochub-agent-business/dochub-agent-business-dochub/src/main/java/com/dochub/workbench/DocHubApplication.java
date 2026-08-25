package com.dochub.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文枢 DocHub —— 智能文档工作台启动类。
 *
 * <p>业务代码在 com.dochub.workbench 下，内部框架（common / id-generator / redisson / lease）
 * 以独立 jar 依赖存在，包仍在 org.javaup 下，因此需要显式声明两个扫描根。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.dochub.workbench", "org.javaup"})
public class DocHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocHubApplication.class, args);
    }
}
