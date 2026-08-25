package com.dochub.workbench.manage.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.util.StrUtil;
import org.apache.tika.Tika;
import com.dochub.workbench.manage.service.DocumentParserService;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;
import com.dochub.workbench.manage.support.DocumentLineClassifier;
import com.dochub.workbench.manage.support.DocumentStructureNodeCandidate;
import com.dochub.workbench.manage.support.DocumentStructureNodeExtractor;
import org.javaup.enums.DocumentContentQualityLevelEnum;
import org.javaup.enums.DocumentFileTypeEnum;
import org.javaup.enums.DocumentStructureLevelEnum;
import org.javaup.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@AllArgsConstructor
@Service
public class TikaDocumentParserService implements DocumentParserService {

    private static final Tika TIKA = new Tika();
    private final DocumentLineClassifier documentLineClassifier;
    private final DocumentStructureNodeExtractor structureNodeExtractor;

    @Override

    public DocumentAnalysisResult parse(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType) {
        //第一步先把不同格式文件转换成统一的原始文本
        String rawText = extractRawText(bytes, originalFileName, mimeType, fileType);
        //再做文本清洗，为后续标题识别、段落切分和结构抽取提供更稳定的输入
        String cleanedText = cleanupText(rawText);
        //结构节点由专门的提取器负责抽取，这些节点后面会参与导航和切块策略判断
        List<DocumentStructureNodeCandidate> structureNodes = structureNodeExtractor.extract(originalFileName, cleanedText);
        int headingCount = countHeadings(cleanedText, structureNodes);
        //段落统计既用于结构判断，也用于后续策略推荐时判断是否适合语义分块。
        List<String> paragraphList = extractParagraphs(cleanedText);
        int maxParagraphLength = paragraphList.stream().mapToInt(String::length).max().orElse(0);
        int charCount = cleanedText.length();
        //token数这里是估算值，不是精确tokenizer结果，但足够用于策略判断和粗粒度统计
        int tokenCount = estimateTokenCount(cleanedText);

        int structureLevel = evaluateStructureLevel(headingCount, paragraphList.size());

        int contentQualityLevel = evaluateContentQuality(cleanedText, charCount);

        return new DocumentAnalysisResult(
            cleanedText,
            charCount,
            tokenCount,
            structureLevel,
            contentQualityLevel,
            headingCount,
            paragraphList.size(),
            maxParagraphLength,
            structureNodes
        );
    }

    @Override
    public String parseTextOnly(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType) {
        // 只做 Tika 提取 + 文本清洗，跳过结构节点抽取（其中包含 LLM 消歧，耗时较高）。
        String rawText = extractRawText(bytes, originalFileName, mimeType, fileType);
        return cleanupText(rawText);
    }

    private String extractRawText(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType) {
        try {
            if (fileType == DocumentFileTypeEnum.PDF || fileType == DocumentFileTypeEnum.DOC || fileType == DocumentFileTypeEnum.DOCX) {

                return TIKA.parseToString(new ByteArrayInputStream(bytes));
            }
            if (fileType == DocumentFileTypeEnum.TXT || fileType == DocumentFileTypeEnum.MD) {

                return new String(bytes, StandardCharsets.UTF_8);
            }
            if (fileType == DocumentFileTypeEnum.HTML) {

                return TIKA.parseToString(new ByteArrayInputStream(bytes));
            }

            return TIKA.parseToString(new ByteArrayInputStream(bytes));
        }
        catch (Exception exception) {

            if (fileType == DocumentFileTypeEnum.TXT || fileType == DocumentFileTypeEnum.MD || mimeType != null && mimeType.startsWith("text/")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            throw new IllegalStateException("Tika 解析失败: " + exception.getMessage(), exception);
        }
    }

    private String cleanupText(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return "";
        }

        String cleaned = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u0000', ' ')
            .replaceAll("[\\t\\x0B\\f]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("[ ]{2,}", " ")
            .trim();
        return cleaned;
    }

    private int countHeadings(String text,
                              List<DocumentStructureNodeCandidate> structureNodes) {
        if (structureNodes != null && !structureNodes.isEmpty()) {
            long structuredHeadingCount = structureNodes.stream()
                .filter(node -> node != null
                    && DocumentStructureNodeTypeEnum.SECTION.getCode().equals(node.getNodeType())
                    && node.getDepth() != null
                    && node.getDepth() > 0)
                .count();
            if (structuredHeadingCount > 0) {
                return (int) structuredHeadingCount;
            }
        }
        int count = 0;
        for (String line : text.split("\n")) {

            if (documentLineClassifier.classify(line).isHeading()) {
                count++;
            }
        }
        return count;
    }

    private List<String> extractParagraphs(String text) {
        List<String> paragraphList = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (StrUtil.isNotBlank(trimmed)) {

                paragraphList.add(trimmed);
            }
        }
        return paragraphList;
    }

    private int estimateTokenCount(String text) {
        int englishWordCount = 0;
        int chineseCharCount = 0;

        for (String word : text.split("\\s+")) {
            if (word.matches(".*[A-Za-z].*")) {
                englishWordCount++;
            }
        }

        for (char current : text.toCharArray()) {
            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                chineseCharCount++;
            }
        }

        return englishWordCount + chineseCharCount + Math.max(1, (text.length() - chineseCharCount) / 4);
    }

    private int evaluateStructureLevel(int headingCount, int paragraphCount) {

        if (headingCount >= 5) {
            return DocumentStructureLevelEnum.HIGH.getCode();
        }
        if (headingCount >= 2) {
            return DocumentStructureLevelEnum.MEDIUM.getCode();
        }
        if (paragraphCount >= 3) {
            return DocumentStructureLevelEnum.LOW.getCode();
        }
        return DocumentStructureLevelEnum.UNKNOWN.getCode();
    }

    private int evaluateContentQuality(String text, int charCount) {
        if (StrUtil.isBlank(text) || charCount < 20) {

            return DocumentContentQualityLevelEnum.LOW.getCode();
        }

        long brokenCharCount = text.chars().filter(value -> value == '�').count();
        double brokenRatio = charCount == 0 ? 1D : (double) brokenCharCount / (double) charCount;
        if (brokenRatio > 0.02D || charCount < 100) {

            return DocumentContentQualityLevelEnum.LOW.getCode();
        }
        if (brokenRatio > 0.005D || charCount < 500) {

            return DocumentContentQualityLevelEnum.MEDIUM.getCode();
        }

        return DocumentContentQualityLevelEnum.HIGH.getCode();
    }
}
