package com.dochub.workbench.chatagent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.model.OpenChatRouteDecision;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Deterministic, zero-model-call routing for open chat. */
@Component
public class OpenChatExecutionRouter {

    private static final Set<String> EXPLICIT_SEARCH = Set.of(
        "搜索", "联网", "上网查", "浏览", "查证", "web search", "browse", "search online"
    );
    private static final Set<String> CURRENT_MARKERS = Set.of(
        "今天", "明天", "昨天", "当前", "最新", "实时", "刚刚", "本周", "本月", "今年"
    );
    private static final Set<String> FRESH_TOPICS = Set.of(
        "天气", "气温", "新闻", "股价", "行情", "汇率", "热搜", "版本", "价格", "赛程", "航班"
    );

    public OpenChatRouteDecision route(String question, String requestedMode) {
        ExecutionMode explicit = parseExplicitMode(requestedMode);
        if (explicit != null) {
            return new OpenChatRouteDecision(explicit, "EXPLICIT_" + explicit.name());
        }
        String normalized = StrUtil.blankToDefault(question, "").trim().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, EXPLICIT_SEARCH)) {
            return new OpenChatRouteDecision(ExecutionMode.REACT_AGENT, "EXPLICIT_SEARCH_INTENT");
        }
        if (containsAny(normalized, CURRENT_MARKERS) && containsAny(normalized, FRESH_TOPICS)) {
            return new OpenChatRouteDecision(ExecutionMode.REACT_AGENT, "CURRENT_INFORMATION_INTENT");
        }
        return new OpenChatRouteDecision(ExecutionMode.DIRECT_CHAT, "ORDINARY_OPEN_CHAT");
    }

    private ExecutionMode parseExplicitMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return null;
        }
        try {
            ExecutionMode parsed = ExecutionMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
            return switch (parsed) {
                case DIRECT_CHAT, REACT_AGENT, PLAN_AND_EXECUTE -> parsed;
                default -> null;
            };
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean containsAny(String text, Set<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }
}
