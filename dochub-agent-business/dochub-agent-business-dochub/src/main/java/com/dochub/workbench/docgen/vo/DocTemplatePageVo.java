package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文枢 DocHub 模板分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocTemplatePageVo {

    private Integer pageNo;

    private Integer pageSize;

    private Long total;

    private List<DocTemplateItemVo> records;
}
