import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockFetch = vi.fn()
global.fetch = mockFetch

const adminApi = await import('../adminApi.js')

describe('adminApi.js', () => {
  beforeEach(() => {
    mockFetch.mockReset()
  })

  describe('addGenre', () => {
    it('calls correct endpoint with genre name', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addGenre('breaking')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/genre')
      expect(opts.method).toBe('POST')
      expect(JSON.parse(opts.body).name).toBe('breaking')
    })
  })

  describe('addJudge', () => {
    it('calls correct endpoint with judge name', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addJudge('Mike')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/judge')
      expect(JSON.parse(opts.body).judgeName).toBe('Mike')
    })
  })

  describe('deleteGenre', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteGenre(5)

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/genre')
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(5)
    })
  })

  describe('deleteJudge', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteJudge(3)

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/judge')
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(3)
    })
  })

  describe('updateGenre', () => {
    it('sends correct payload', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.updateGenre(1, 'popping')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/update-genre')
      const body = JSON.parse(opts.body)
      expect(body.id).toBe(1)
      expect(body.newName).toBe('popping')
    })
  })

  describe('getFeedbackGroups', () => {
    it('returns parsed JSON on success', async () => {
      const groups = [{ id: 1, name: 'Energy' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(groups) })

      const result = await adminApi.getFeedbackGroups()

      expect(result).toEqual(groups)
    })

    it('returns empty array on failure', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false })

      const result = await adminApi.getFeedbackGroups()

      expect(result).toEqual([])
    })
  })

  describe('addFeedbackGroup', () => {
    it('calls correct endpoint', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addFeedbackGroup('Energy')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/feedback-group')
      expect(opts.method).toBe('POST')
      expect(JSON.parse(opts.body).name).toBe('Energy')
    })
  })

  describe('deleteFeedbackGroup', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteFeedbackGroup(2)

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/feedback-group')
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(2)
    })
  })

  describe('addFeedbackTag', () => {
    it('sends groupId and label', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.addFeedbackTag(1, 'High Energy')

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/feedback-tag')
      expect(opts.method).toBe('POST')
      const body = JSON.parse(opts.body)
      expect(body.groupId).toBe(1)
      expect(body.label).toBe('High Energy')
    })
  })

  describe('deleteFeedbackTag', () => {
    it('sends DELETE with id', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await adminApi.deleteFeedbackTag(7)

      const [url, opts] = mockFetch.mock.calls[0]
      expect(url).toBe('/api/v1/admin/feedback-tag')
      expect(opts.method).toBe('DELETE')
      expect(JSON.parse(opts.body).id).toBe(7)
    })
  })
})
