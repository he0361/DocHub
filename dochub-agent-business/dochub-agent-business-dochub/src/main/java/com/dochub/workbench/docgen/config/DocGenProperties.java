package com.dochub.workbench.docgen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文枢 DocHub 文档生成配置（app.manage.docgen.*）。
 */
@Data
@ConfigurationProperties(prefix = "app.manage.docgen")
public class DocGenProperties {

    /** 大纲规划模型调用超时（毫秒） */
    private long outlineTimeoutMs = 60000L;

    /** 正文生成模型调用超时（毫秒） */
    private long bodyTimeoutMs = 180000L;

    /** 大纲最多保留条目数 */
    private int maxOutlineItems = 30;

    /** 生成并发线程数 */
    private int executorThreads = 4;

    /** 参考文档仿写：参考正文单次送入模型的最大字符数 */
    private int referenceMaxChars = 8000;

    /**
     * 文档生成（仿写）使用的模型；为空时使用全局聊天模型（yaml 的 spring.ai.openai.chat.options.model）。
     */
    private String model = "";
}
