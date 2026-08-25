package com.dochub.workbench.chatagent.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.dochub.workbench.chatagent.data.DochubChatStageBenchmark;
import com.dochub.workbench.chatagent.mapper.DochubChatStageBenchmarkMapper;
import com.dochub.workbench.chatagent.model.StageBenchmarkView;
import org.javaup.enums.BusinessStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

@Slf4j
@Service
public class StageBenchmarkService {

    private static final int MAX_RECENT_SAMPLES = 200;
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};

    private final DochubChatStageBenchmarkMapper benchmarkMapper;
    private final ObjectMapper objectMapper;

    @Resource
    private UidGenerator uidGenerator;

    public StageBenchmarkService(DochubChatStageBenchmarkMapper benchmarkMapper,
                                 ObjectMapper objectMapper) {
        this.benchmarkMapper = benchmarkMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordDuration(String stageCode, String executionMode, long durationMs) {
        if (StrUtil.isBlank(stageCode) || durationMs < 0) {
            return;
        }
        String normalizedMode = StrUtil.blankToDefault(executionMode, "UNKNOWN");
        DochubChatStageBenchmark existing = benchmarkMapper.selectOne(
            new LambdaQueryWrapper<DochubChatStageBenchmark>()
                .eq(DochubChatStageBenchmark::getStageCode, stageCode)
                .eq(DochubChatStageBenchmark::getExecutionMode, normalizedMode)
                .last("LIMIT 1")
        );

        if (existing == null) {
            DochubChatStageBenchmark benchmark = new DochubChatStageBenchmark();
            benchmark.setId(uidGenerator.getUid());
            benchmark.setStageCode(stageCode);
            benchmark.setExecutionMode(normalizedMode);
            List<Long> durations = new ArrayList<>();
            durations.add(durationMs);
            benchmark.setRecentDurations(writeJson(durations));
            benchmark.setSampleCount(1);
            benchmark.setAvgDurationMs(durationMs);
            benchmark.setP50DurationMs(durationMs);
            benchmark.setP90DurationMs(durationMs);
            benchmark.setP99DurationMs(durationMs);
            benchmark.setMaxDurationMs(durationMs);
            benchmark.setMinDurationMs(durationMs);
            benchmark.setLastUpdateTime(new Date());
            benchmark.setStatus(BusinessStatus.YES.getCode());
            benchmarkMapper.insert(benchmark);
            return;
        }

        List<Long> durations = readDurations(existing.getRecentDurations());
        durations.add(durationMs);
        if (durations.size() > MAX_RECENT_SAMPLES) {
            durations = new ArrayList<>(durations.subList(durations.size() - MAX_RECENT_SAMPLES, durations.size()));
        }

        List<Long> sorted = new ArrayList<>(durations);
        Collections.sort(sorted);
        int size = sorted.size();

        benchmarkMapper.update(null,
            new LambdaUpdateWrapper<DochubChatStageBenchmark>()
                .eq(DochubChatStageBenchmark::getId, existing.getId())
                .set(DochubChatStageBenchmark::getRecentDurations, writeJson(durations))
                .set(DochubChatStageBenchmark::getSampleCount, size)
                .set(DochubChatStageBenchmark::getAvgDurationMs, sorted.stream().mapToLong(Long::longValue).sum() / size)
                .set(DochubChatStageBenchmark::getP50DurationMs, sorted.get((int) (size * 0.5)))
                .set(DochubChatStageBenchmark::getP90DurationMs, sorted.get(Math.min((int) (size * 0.9), size - 1)))
                .set(DochubChatStageBenchmark::getP99DurationMs, sorted.get(Math.min((int) (size * 0.99), size - 1)))
                .set(DochubChatStageBenchmark::getMaxDurationMs, sorted.get(size - 1))
                .set(DochubChatStageBenchmark::getMinDurationMs, sorted.get(0))
                .set(DochubChatStageBenchmark::getLastUpdateTime, new Date())
        );
    }

    @Transactional(readOnly = true)
    public List<StageBenchmarkView> listAll() {
        return benchmarkMapper.selectList(
                new LambdaQueryWrapper<DochubChatStageBenchmark>()
                    .eq(DochubChatStageBenchmark::getStatus, BusinessStatus.YES.getCode())
                    .orderByAsc(DochubChatStageBenchmark::getStageCode)
            )
            .stream()
            .map(this::toView)
            .toList();
    }

    private StageBenchmarkView toView(DochubChatStageBenchmark entity) {
        return new StageBenchmarkView(
            entity.getStageCode(),
            entity.getExecutionMode(),
            entity.getP50DurationMs(),
            entity.getP90DurationMs(),
            entity.getP99DurationMs(),
            entity.getAvgDurationMs(),
            entity.getMaxDurationMs(),
            entity.getMinDurationMs(),
            entity.getSampleCount() == null ? 0 : entity.getSampleCount()
        );
    }

    private List<Long> readDurations(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<Long> result = objectMapper.readValue(json, LONG_LIST_TYPE);
            return result == null ? new ArrayList<>() : new ArrayList<>(result);
        } catch (Exception exception) {
            log.warn("解析性能基准耗时记录失败", exception);
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("序列化性能基准数据失败", exception);
        }
    }
}
