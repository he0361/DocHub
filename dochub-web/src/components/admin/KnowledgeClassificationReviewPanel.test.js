import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import KnowledgeClassificationReviewPanel from './KnowledgeClassificationReviewPanel.vue'

const review = {
  reviewId: '7',
  documentId: '9',
  documentName: 'DocHub 模型配置',
  version: 4,
  reason: '现有知识域相似度处于审核区间',
  proposedScopeJson: JSON.stringify({
    scopeCode: 'new_scope',
    scopeName: 'LLM 建议的新知识域',
    topicCode: 'model_config',
    topicName: '模型配置'
  }),
  candidateJson: JSON.stringify([
    { route: { routeCode: 'dochub', routeName: 'DocHub 项目' }, combinedScore: 0.78, reason: '项目标识一致' }
  ])
}

describe('KnowledgeClassificationReviewPanel', () => {
  it('requires an explicit choice before resolving a pending review', async () => {
    render(KnowledgeClassificationReviewPanel, {
      props: {
        review,
        scopes: [{ scopeCode: 'dochub', scopeName: 'DocHub 项目' }],
        topics: [{ topicCode: 'model', topicName: '模型', scopeCode: 'dochub' }]
      }
    })

    expect(screen.getByText(/系统未能高置信度确认知识域/)).toBeTruthy()
    expect(screen.getByRole('button', { name: '确认使用已有知识域' }).disabled).toBe(true)
    expect(screen.getByRole('button', { name: '相信 LLM 创建' })).toBeTruthy()

    await fireEvent.update(screen.getByLabelText('已有知识域'), 'dochub')
    expect(screen.getByRole('button', { name: '确认使用已有知识域' }).disabled).toBe(false)
  })
})
