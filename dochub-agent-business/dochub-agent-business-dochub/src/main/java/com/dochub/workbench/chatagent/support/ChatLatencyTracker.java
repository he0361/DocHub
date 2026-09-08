package com.dochub.workbench.chatagent.support;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.chatagent.model.debug.ChatLatencyTrace;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe, non-blocking measurements for one chat exchange. */
public final class ChatLatencyTracker {

    private final Clock clock;
    private final AtomicLong acceptedAt = new AtomicLong();
    private final AtomicLong firstTokenAt = new AtomicLong();
    private final AtomicLong completedAt = new AtomicLong();
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicInteger promptCharacters = new AtomicInteger();
    private final AtomicLong modelConfigVersion = new AtomicLong();
    private final AtomicReference<String> terminalState = new AtomicReference<>("RUNNING");
    private final Map<String, Long> stageStartedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> stageTimings = new ConcurrentHashMap<>();

    public ChatLatencyTracker() {
        this(Clock.systemUTC());
    }

    public ChatLatencyTracker(Clock clock) {
        this.clock = clock;
    }

    public void accepted() {
        acceptedAt.compareAndSet(0L, clock.millis());
        startStage("REQUEST_ACCEPTED");
    }

    public void startStage(String stage) {
        if (StrUtil.isNotBlank(stage)) {
            stageStartedAt.putIfAbsent(stage, clock.millis());
        }
    }

    public void finishStage(String stage) {
        Long start = stageStartedAt.get(stage);
        if (start != null) {
            stageTimings.putIfAbsent(stage, Math.max(0L, clock.millis() - start));
        }
    }

    public void onModelRequest(int characters, long configVersion) {
        modelCalls.incrementAndGet();
        promptCharacters.addAndGet(Math.max(0, characters));
        modelConfigVersion.set(Math.max(0L, configVersion));
        startStage("MODEL_REQUEST");
    }

    public void onToolCall() {
        toolCalls.incrementAndGet();
        startStage("TOOL");
    }

    public void onTextChunk(String chunk) {
        if (StrUtil.isNotBlank(chunk)) {
            firstTokenAt.compareAndSet(0L, clock.millis());
            if (firstTokenAt.get() > 0L) {
                finishStage("MODEL_REQUEST");
                stageTimings.putIfAbsent("FIRST_TOKEN", Math.max(0L, firstTokenAt.get() - acceptedTime()));
            }
        }
    }

    public void complete() {
        finish("COMPLETED");
    }

    public void fail() {
        finish("FAILED");
    }

    public void cancel() {
        finish("CANCELLED");
    }

    private void finish(String state) {
        completedAt.compareAndSet(0L, clock.millis());
        terminalState.compareAndSet("RUNNING", state);
        stageTimings.putIfAbsent("COMPLETE", Math.max(0L, completedAt.get() - acceptedTime()));
    }

    public ChatLatencyTrace snapshot() {
        long accepted = acceptedTime();
        long first = firstTokenAt.get();
        long completed = completedAt.get();
        return new ChatLatencyTrace(
            first == 0L ? null : Math.max(0L, first - accepted),
            completed == 0L ? null : Math.max(0L, completed - accepted),
            modelCalls.get(),
            toolCalls.get(),
            promptCharacters.get(),
            modelConfigVersion.get(),
            Map.copyOf(new LinkedHashMap<>(stageTimings)),
            terminalState.get()
        );
    }

    private long acceptedTime() {
        long accepted = acceptedAt.get();
        if (accepted != 0L) {
            return accepted;
        }
        accepted();
        return acceptedAt.get();
    }
}
