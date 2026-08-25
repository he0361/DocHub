package com.dochub.workbench.manage.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 支撑组件
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResult {

    private String parsedText;

    private Integer charCount;

    private Integer tokenCount;

    private Integer structureLevel;

    private Integer contentQualityLevel;

    private Integer headingCount;

    private Integer paragraphCount;

    private Integer maxParagraphLength;

    private List<DocumentStructureNodeCandidate> structureNodes = new ArrayList<>();
}
