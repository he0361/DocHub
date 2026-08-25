package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文枢 DocHub 文档生成结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocGenGenerateVo {

    private String recordCode;

    private String templateName;

    private String fileName;

    /** 大纲（LLM 产出，失败时回退模板自带） */
    private List<String> outline;

    /** 生成的 Markdown 正文预览 */
    private String previewMarkdown;

    private Integer generationStatus;
}
