package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.dto.DocumentIndexBuildDto;
import com.dochub.workbench.manage.dto.DocumentChunkQueryDto;
import com.dochub.workbench.manage.dto.DocumentChunkDetailQueryDto;
import com.dochub.workbench.manage.dto.DocumentDetailQueryDto;
import com.dochub.workbench.manage.dto.DocumentDeleteDto;
import com.dochub.workbench.manage.dto.DocumentPageQueryDto;
import com.dochub.workbench.manage.dto.DocumentStrategyConfirmDto;
import com.dochub.workbench.manage.dto.DocumentStrategyPlanQueryDto;
import com.dochub.workbench.manage.dto.DocumentTaskLogQueryDto;
import com.dochub.workbench.manage.dto.DocumentUploadDto;
import com.dochub.workbench.manage.vo.DocumentIndexBuildProgressVo;
import com.dochub.workbench.manage.vo.DocumentIndexBuildVo;
import com.dochub.workbench.manage.vo.DocumentChunkQueryVo;
import com.dochub.workbench.manage.vo.DocumentChunkDetailVo;
import com.dochub.workbench.manage.vo.DocumentDeleteVo;
import com.dochub.workbench.manage.vo.DocumentListItemVo;
import com.dochub.workbench.manage.vo.DocumentPageQueryVo;
import com.dochub.workbench.manage.vo.DocumentStrategyConfirmVo;
import com.dochub.workbench.manage.vo.DocumentStrategyPlanQueryVo;
import com.dochub.workbench.manage.vo.DocumentTaskLogQueryVo;
import com.dochub.workbench.manage.vo.DocumentUploadVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentManageService {

    DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto);

    /**
     * 工作台生成文档一键入库：把 Markdown 正文作为 .md 文档走标准解析/切块/索引链路。
     */
    DocumentUploadVo ingestGeneratedText(String documentName, String markdownContent, DocumentUploadDto dto);

    DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto);

    DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto);

    DocumentDeleteVo deleteDocument(DocumentDeleteDto dto);

    DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto);

    DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto);

    DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto);

    DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto);

    DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto);

    DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto);

    /**
     * 查询某文档索引构建的实时进度（内存态，仅构建进行中有值）。
     */
    DocumentIndexBuildProgressVo queryIndexBuildProgress(Long documentId);
}
