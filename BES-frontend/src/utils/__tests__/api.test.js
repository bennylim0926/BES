import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

// Import AFTER mocking fetch
const api = await import('../api.js')

describe('api.js', () => {
  beforeEach(() => {
    mockFetch.mockReset()
  })

  describe('login', () => {
    it('should call /api/v1/auth/login with correct payload', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await api.login('admin', 'password123')

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username: 'admin', password: 'password123' }),
      })
    })
  })

  describe('logout', () => {
    it('should call /api/v1/auth/logout with POST', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await api.logout()

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
        },
      })
    })
  })

  describe('fetchAllEvents', () => {
    it('should return parsed JSON on success', async () => {
      const mockEvents = [{ eventId: 1, eventName: 'Test Event' }]
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockEvents),
      })

      const result = await api.fetchAllEvents()

      expect(result).toEqual(mockEvents)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/event/events', {
        method: 'GET',
        credentials: 'include',
      })
    })
  })

  describe('getParticipantScore', () => {
    it('should return scores on success', async () => {
      const mockScores = [{ id: 1, value: 8.5 }]
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockScores),
      })

      const result = await api.getParticipantScore('TestEvent')
      expect(result).toEqual(mockScores)
    })

    it('should return empty array on 404', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 404 })

      const result = await api.getParticipantScore('NonExistent')
      expect(result).toEqual([])
    })
  })

  describe('submitParticipantScore', () => {
    it('should send correct payload structure', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      const participants = [{ name: 'Player1', score: 9 }]
      await api.submitParticipantScore('Event1', 'Breaking', 'Judge1', participants)

      const call = mockFetch.mock.calls[0]
      const body = JSON.parse(call[1].body)

      expect(body.eventName).toBe('Event1')
      expect(body.genreName).toBe('Breaking')
      expect(body.judgeName).toBe('Judge1')
      expect(body.participantScore).toEqual(participants)
    })
  })

  describe('whoami', () => {
    it('returns parsed JSON when ok', async () => {
      const user = { authenticated: true, username: 'admin' }
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(user) })

      const result = await api.whoami()

      expect(result).toEqual(user)
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/auth/me'),
        expect.objectContaining({ credentials: 'include' })
      )
    })
  })

  describe('fetchAllGenres', () => {
    it('returns genres on success', async () => {
      const genres = [{ id: 1, genreName: 'breaking' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(genres) })

      const result = await api.fetchAllGenres()

      expect(result).toEqual(genres)
    })

    it('returns empty array on failure', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 500 })

      const result = await api.fetchAllGenres()

      expect(result).toEqual([])
    })
  })

  describe('getAllJudges', () => {
    it('returns judges on success', async () => {
      const judges = [{ judgeId: 1, judgeName: 'Mike' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(judges) })

      const result = await api.getAllJudges()

      expect(result).toEqual(judges)
    })

    it('returns empty array on failure', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 500 })

      const result = await api.getAllJudges()

      expect(result).toEqual([])
    })
  })

  describe('getRegisteredParticipantsByEvent', () => {
    it('returns participants on success', async () => {
      const participants = [{ participantName: 'Player1' }]
      mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(participants) })

      const result = await api.getRegisteredParticipantsByEvent('Fest')

      expect(result).toEqual(participants)
    })

    it('returns empty array on 404', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 404 })

      const result = await api.getRegisteredParticipantsByEvent('Missing')

      expect(result).toEqual([])
    })
  })
})
