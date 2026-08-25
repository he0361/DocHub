package com.dochub.workbench.manage.support;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import org.javaup.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 支撑组件
 * @author: zhangjihe
 **/
@AllArgsConstructor
@Component
public class DocumentStructureNodeExtractor {
    //第一阶段，信号提取器，负责将文本逐行扫描并识别出标题、列表、正文等结构信号
    private final DocumentStructureSignalExtractor signalExtractor;
    //第二阶段，歧义消解器，对低置信度的辅助信号LLM进行二次判定
    private final DocumentStructureAmbiguityResolver ambiguityResolver;
    //第三阶段，层级解析器，将扁平的信号列表组装成带有父子关系的草稿树
    private final DocumentStructureHierarchyResolver hierarchyResolver;
    //第四阶段，树校验器，修复无效父节点、重算深度、重建路径，输出最终候选节点
    private final DocumentStructureTreeValidator treeValidator;

    public List<DocumentStructureNodeCandidate> extract(String documentTitle, String parsedText) {
        //对标题和文本做空值保护和去除首尾空白
        String normalizedTitle = StrUtil.blankToDefault(documentTitle, "文档").trim();
        String normalizedText = StrUtil.blankToDefault(parsedText, "").trim();
        //如果正文为空，说明当前文档没有任何可供解析的结构内容；此时直接返回一个根节点，既能保证数据结构稳定，也能让下游明确知道“文档存在但无结构”
        if (normalizedText.isBlank()) {
            return List.of(new DocumentStructureNodeCandidate(
                1,
                DocumentStructureNodeTypeEnum.DOCUMENT.getCode(),
                null,
                0,
                0,
                0,
                "",
                normalizedTitle,
                normalizedTitle,
                "/document",
                "",
                "",
                null
            ));
        }
        //第一阶段，信号提取-逐行扫描文本，识别标题、列表、噪声等结构信号
        DocumentStructureSignalBatch signalBatch=signalExtractor.extract(normalizedTitle,normalizedText);
        //从信号批次中取出原始信号列表(防御性控制处理)
        List<DocumentStructureSignal> rawSignals=signalBatch==null?List.of():signalBatch.signals();
        //从信号批次中取出所有行的规范化文本，供歧义消解时作为上下文窗口使用
        List<String> allLines = signalBatch==null?List.of():signalBatch.contextLines();
        //第二阶段：歧义消除-对“像标题又像列表”的候选行做二次判定，目的是减少误把列表当标题、或误把标题当正文的情况
        List<DocumentStructureSignal> resolvedSignals = ambiguityResolver.resolve(normalizedTitle,allLines,rawSignals);
        //第三阶段：层级构建-将扁平信号列表组装为带有父子关系的草稿节点树
        List<DocumentStructureNodeDraft> drafts = hierarchyResolver.resolve(normalizedTitle,resolvedSignals);
        //第四阶段：树校验与构建-修复层级异常、重算深度、重建路径，输出最终候选节点。
        return treeValidator.validateAndBuild(normalizedTitle, drafts);
    }
}
