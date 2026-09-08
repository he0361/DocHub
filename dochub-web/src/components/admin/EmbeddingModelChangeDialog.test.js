import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it } from 'vitest'
import EmbeddingModelChangeDialog from './EmbeddingModelChangeDialog.vue'
import EmbeddingMigrationProgress from './EmbeddingMigrationProgress.vue'

afterEach(cleanup)

describe('EmbeddingModelChangeDialog', () => {
  it('requires both password and the exact Chinese confirmation phrase', async () => {
    render(EmbeddingModelChangeDialog, {
      props: {
        open: true,
        candidate: { modelName: 'b' },
        active: { modelName: 'a' },
        changeMode: 'BLUE_GREEN_REBUILD'
      }
    })

    await fireEvent.update(screen.getByLabelText('再次输入管理员密码'), 'secret')
    await fireEvent.update(screen.getByLabelText('确认文本'), '我确认修改向量模型')
    expect(screen.getByRole('button', { name: '开始安全更换' }).disabled).toBe(true)

    await fireEvent.update(screen.getByLabelText('确认文本'), '我确认更改向量模型')
    expect(screen.getByRole('button', { name: '开始安全更换' }).disabled).toBe(false)
  })

  it('clears second-factor secrets immediately after submit', async () => {
    const { emitted } = render(EmbeddingModelChangeDialog, {
      props: { open: true, candidate: {}, active: {}, changeMode: 'HOT_SWAP' }
    })
    const password = screen.getByLabelText('再次输入管理员密码')
    const phrase = screen.getByLabelText('确认文本')
    await fireEvent.update(password, 'secret')
    await fireEvent.update(phrase, '我确认更改向量模型')
    await fireEvent.click(screen.getByRole('button', { name: '开始安全更换' }))

    expect(emitted().confirm[0][0]).toEqual({
      currentPassword: 'secret',
      confirmationPhrase: '我确认更改向量模型'
    })
    expect(password.value).toBe('')
    expect(phrase.value).toBe('')
  })
})

describe('EmbeddingMigrationProgress', () => {
  it('explains that the old model remains active during rebuild', () => {
    render(EmbeddingMigrationProgress, {
      props: {
        migration: {
          status: 'REBUILDING_DOCUMENTS',
          sourceConfigVersion: 7,
          targetConfigVersion: 8,
          documentTotal: 10,
          documentProcessed: 4,
          memoryTotal: 2,
          memoryProcessed: 0
        }
      }
    })
    expect(screen.getByText(/后台重建完成并校验通过后才会切换/)).toBeTruthy()
    expect(screen.getByText(/当前用户仍在使用旧向量模型/)).toBeTruthy()
  })
})
