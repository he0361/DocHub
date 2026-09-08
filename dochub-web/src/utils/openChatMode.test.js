import { describe, expect, it } from 'vitest'
import { DEFAULT_OPEN_CHAT_MODE, OPEN_CHAT_MODES } from './openChatMode'

describe('open chat mode defaults', () => {
  it('uses one-call direct chat unless the user explicitly selects an agent', () => {
    expect(DEFAULT_OPEN_CHAT_MODE).toBe(OPEN_CHAT_MODES.DIRECT_CHAT)
  })
})
