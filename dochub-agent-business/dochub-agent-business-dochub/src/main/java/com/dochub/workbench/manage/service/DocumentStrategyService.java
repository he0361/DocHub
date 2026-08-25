package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubDocumentStrategyPlan;
import com.dochub.workbench.manage.data.DochubDocumentStrategyStep;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;
import com.dochub.workbench.manage.support.DocumentStrategyPlanDraft;
import com.dochub.workbench.manage.support.ParentBlockCandidate;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentStrategyService {

    DocumentStrategyPlanDraft recommendStrategy(DochubDocument document, DocumentAnalysisResult analysisResult);

    List<DochubDocumentStrategyStep> normalizeSteps(DochubDocumentStrategyPlan basePlan,
                                                        List<DochubDocumentStrategyStep> baseSteps,
                                                        List<Integer> requestParentStrategyTypes,
                                                        List<Integer> requestChildStrategyTypes,
                                                        Long documentId);

    List<ParentBlockCandidate> buildParentBlocks(DochubDocument document,
                                                 DochubDocumentStrategyPlan plan,
                                                 List<DochubDocumentStrategyStep> steps,
                                                 String parsedText);
}
