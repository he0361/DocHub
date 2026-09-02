package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.dto.KnowledgeClassificationReviewQueryDto;
import com.dochub.workbench.manage.dto.KnowledgeClassificationResolveDto;
import com.dochub.workbench.manage.vo.KnowledgeClassificationReviewVo;

import java.util.List;

public interface KnowledgeClassificationReviewService {
    List<KnowledgeClassificationReviewVo> list(KnowledgeClassificationReviewQueryDto dto);
    KnowledgeClassificationReviewVo detail(KnowledgeClassificationReviewQueryDto dto);
    KnowledgeClassificationReviewVo resolve(String operator, KnowledgeClassificationResolveDto dto);
}
