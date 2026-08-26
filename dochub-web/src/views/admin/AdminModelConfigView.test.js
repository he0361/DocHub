import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import AdminModelConfigView from './AdminModelConfigView.vue'

vi.mock('../../api/api', () => ({
  modelConfigApi: {
    query: vi.fn().mockResolvedValue({}),
    testChat: vi.fn(),
    saveChat: vi.fn()
  }
}))

describe('AdminModelConfigView', () => {
  it('shows the global-impact warning and final URL preview', async () => {
    render(AdminModelConfigView)

    expect(screen.getByText(/影响所有用户和后台文档任务/)).toBeTruthy()

    await fireEvent.update(screen.getByLabelText('Base URL'), 'http://127.0.0.1:11434')
    await fireEvent.update(screen.getByLabelText('请求路径'), '/v1/chat/completions')

    expect(screen.getByText('http://127.0.0.1:11434/v1/chat/completions')).toBeTruthy()
  })
})
