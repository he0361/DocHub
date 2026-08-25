package com.dochub.workbench.chatagent.rag.model;

/**
 * 计划-执行（Plan-and-Execute）的单个执行步骤。
 *
 * <p>由 Planner 生成 {@code title}，执行器逐步用带工具的 Agent 执行并把结果文本累积到 {@code result}，
 * 供最后的汇总阶段综合。</p>
 */
public class PlanStep {

    /** 步骤描述 */
    private final String title;

    /** 该步骤执行结果的累积文本 */
    private final StringBuilder result = new StringBuilder();

    public PlanStep(String title) {
        this.title = title == null ? "" : title.trim();
    }

    public String getTitle() {
        return title;
    }

    public void appendResult(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (result.length() > 0) {
            result.append('\n');
        }
        result.append(text);
    }

    public String getResult() {
        return result.toString().trim();
    }
}
