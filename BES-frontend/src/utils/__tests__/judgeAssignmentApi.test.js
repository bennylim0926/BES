import { describe, it, expect, vi, beforeEach } from 'vitest'

// Inline the functions under test so tests are self-contained
const domain = ''

const toggleJudgeAssignmentMode = async (eventName, categoryId, enabled) => {
  return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${categoryId}/judge-assignment-mode`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled })
  })
}

const getParticipantsByJudge = async (eventName, categoryId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${categoryId}/participants-by-judge`, {
      credentials: 'include'
    })
    if (!res.ok) return {}
    return await res.json()
  } catch { return {} }
}

describe('toggleJudgeAssignmentMode', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })

  it('POSTs to the correct URL with enabled:true', async () => {
    fetch.mockResolvedValueOnce({ ok: true })
    await toggleJudgeAssignmentMode('Showcase', 42, true)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/event/Showcase/categories/42/judge-assignment-mode',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ enabled: true }) })
    )
  })

  it('POSTs to the correct URL with enabled:false', async () => {
    fetch.mockResolvedValueOnce({ ok: true })
    await toggleJudgeAssignmentMode('My Event', 99, false)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/event/My%20Event/categories/99/judge-assignment-mode',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ enabled: false }) })
    )
  })

  it('includes credentials:include', async () => {
    fetch.mockResolvedValueOnce({ ok: true })
    await toggleJudgeAssignmentMode('Showcase', 42, true)
    expect(fetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ credentials: 'include' })
    )
  })
})

describe('getParticipantsByJudge', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })

  it('returns parsed JSON on success', async () => {
    fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ Jay: [{ auditionNumber: 1, participantName: 'Alice' }] }) })
    const result = await getParticipantsByJudge('Showcase', 5)
    expect(result).toEqual({ Jay: [{ auditionNumber: 1, participantName: 'Alice' }] })
  })

  it('encodes eventName in URL', async () => {
    fetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })
    await getParticipantsByJudge('My Event', 5)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/event/My%20Event/categories/5/participants-by-judge',
      expect.any(Object)
    )
  })

  it('returns empty object on fetch failure', async () => {
    fetch.mockRejectedValueOnce(new Error('network'))
    const result = await getParticipantsByJudge('Showcase', 5)
    expect(result).toEqual({})
  })

  it('returns empty object on non-ok response', async () => {
    fetch.mockResolvedValueOnce({ ok: false, status: 404 })
    const result = await getParticipantsByJudge('Showcase', 5)
    expect(result).toEqual({})
  })

  it('includes credentials:include', async () => {
    fetch.mockResolvedValueOnce({ ok: true, json: async () => ({}) })
    await getParticipantsByJudge('Showcase', 5)
    expect(fetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ credentials: 'include' })
    )
  })
})
