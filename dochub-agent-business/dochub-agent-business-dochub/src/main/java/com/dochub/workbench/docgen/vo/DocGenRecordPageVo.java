package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文枢 DocHub 生成历史分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocGenRecordPageVo {

    private Integer pageNo;

    private Integer pageSize;

    private Long total;

    private List<DocGenRecordItemVo> records;
}
