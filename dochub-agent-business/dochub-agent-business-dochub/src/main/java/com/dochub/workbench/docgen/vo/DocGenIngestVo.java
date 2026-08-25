package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 一键入库结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocGenIngestVo {

    private Long documentId;

    private Long taskId;

    private String documentName;
}
