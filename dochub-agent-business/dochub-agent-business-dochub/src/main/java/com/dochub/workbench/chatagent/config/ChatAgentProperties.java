package com.dochub.workbench.chatagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 配置属性
 * @author: zhangjihe
 **/
@ConfigurationProperties(prefix = "app.chat")
public class ChatAgentProperties {

    private boolean recommendationEnabled = true;
    private int maxModelCallsPerRun = 8;
    private int maxModelCallsPerThread = 40;
    private int maxToolCallsPerRun = 6;
    private int maxToolCallsPerThread = 30;
    private int historyPreviewTurns = 4;
    private long recommendationTimeoutMs = 3000L;
    private String systemPrompt = "";
    private String recommendationPrompt = "";

    /** 计划-执行（Plan-and-Execute）能力开关 */
    private boolean planExecuteEnabled = true;
    /** 计划-执行最多拆几步，超过会被截断（预算护栏） */
    private int planMaxSteps = 5;

    public boolean isRecommendationEnabled() {
        return recommendationEnabled;
    }

    public void setRecommendationEnabled(boolean recommendationEnabled) {
        this.recommendationEnabled = recommendationEnabled;
    }

    public int getMaxModelCallsPerRun() {
        return maxModelCallsPerRun;
    }

    public void setMaxModelCallsPerRun(int maxModelCallsPerRun) {
        this.maxModelCallsPerRun = maxModelCallsPerRun;
    }

    public int getMaxModelCallsPerThread() {
        return maxModelCallsPerThread;
    }

    public void setMaxModelCallsPerThread(int maxModelCallsPerThread) {
        this.maxModelCallsPerThread = maxModelCallsPerThread;
    }

    public int getMaxToolCallsPerRun() {
        return maxToolCallsPerRun;
    }

    public void setMaxToolCallsPerRun(int maxToolCallsPerRun) {
        this.maxToolCallsPerRun = maxToolCallsPerRun;
    }

    public int getMaxToolCallsPerThread() {
        return maxToolCallsPerThread;
    }

    public void setMaxToolCallsPerThread(int maxToolCallsPerThread) {
        this.maxToolCallsPerThread = maxToolCallsPerThread;
    }

    public int getHistoryPreviewTurns() {
        return historyPreviewTurns;
    }

    public void setHistoryPreviewTurns(int historyPreviewTurns) {
        this.historyPreviewTurns = historyPreviewTurns;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getRecommendationPrompt() {
        return recommendationPrompt;
    }

    public void setRecommendationPrompt(String recommendationPrompt) {
        this.recommendationPrompt = recommendationPrompt;
    }

    public long getRecommendationTimeoutMs() {
        return recommendationTimeoutMs;
    }

    public void setRecommendationTimeoutMs(long recommendationTimeoutMs) {
        this.recommendationTimeoutMs = recommendationTimeoutMs;
    }

    public boolean isPlanExecuteEnabled() {
        return planExecuteEnabled;
    }

    public void setPlanExecuteEnabled(boolean planExecuteEnabled) {
        this.planExecuteEnabled = planExecuteEnabled;
    }

    public int getPlanMaxSteps() {
        return planMaxSteps;
    }

    public void setPlanMaxSteps(int planMaxSteps) {
        this.planMaxSteps = planMaxSteps;
    }
}
