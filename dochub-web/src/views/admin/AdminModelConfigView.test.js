import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminModelConfigView from './AdminModelConfigView.vue'

vi.mock('../../api/api', () => ({
  modelConfigApi: {
    query: vi.fn().mockResolvedValue({}),
    queryEmbedding: vi.fn().mockResolvedValue({}),
    testChat: vi.fn(),
    saveChat: vi.fn(),
    testEmbedding: vi.fn(),
    changeEmbedding: vi.fn(),
    retryEmbeddingMigration: vi.fn(),
    rollbackEmbedding: vi.fn()
  }
}))

describe('AdminModelConfigView', () => {
  afterEach(() => cleanup())

  it('shows the global-impact warning and final URL preview', async () => {
    render(AdminModelConfigView)

    expect(screen.getByText(/影响所有用户和后台文档任务/)).toBeTruthy()

    await fireEvent.update(screen.getAllByLabelText('Base URL')[0], 'http://127.0.0.1:11434')
    await fireEvent.update(screen.getAllByLabelText('请求路径')[0], '/v1/chat/completions')

    expect(screen.getByText('http://127.0.0.1:11434/v1/chat/completions')).toBeTruthy()
  })

  it('does not ask a local chat model for an API Key', async () => {
    const { container } = render(AdminModelConfigView)

    const chatForm = within(container.querySelectorAll('form')[0])
    await fireEvent.update(chatForm.getByLabelText('部署类型'), 'LOCAL')

    expect(chatForm.queryByLabelText('API Key')).toBeNull()
    expect(chatForm.getByText('本地服务无需填写 API Key；保存时会自动清除原有远程凭证。')).toBeTruthy()
  })

  it('does not ask a local embedding model for an API Key', async () => {
    const { container } = render(AdminModelConfigView)

    const embeddingForm = within(container.querySelectorAll('form')[1])
    await fireEvent.update(embeddingForm.getByLabelText('部署类型'), 'LOCAL')

    expect(embeddingForm.queryByLabelText('API Key')).toBeNull()
    expect(embeddingForm.getByText('本地服务无需填写 API Key；保存时会自动清除原有远程凭证。')).toBeTruthy()
  })
})
