import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store = {}
  return {
    getItem: (key) => store[key] ?? null,
    setItem: (key, val) => { store[key] = String(val) },
    removeItem: (key) => { delete store[key] },
    clear: () => { store = {} },
  }
})()
Object.defineProperty(global, 'sessionStorage', { value: sessionStorageMock })

// Mock localStorage
const localStorageMock = (() => {
  let store = {}
  return {
    getItem: (key) => store[key] ?? null,
    setItem: (key, val) => { store[key] = String(val) },
    removeItem: (key) => { delete store[key] },
    clear: () => { store = {} },
  }
})()
Object.defineProperty(global, 'localStorage', { value: localStorageMock })

// Mock pinia (defineStore needs a Pinia instance; stub it for utility-only tests)
vi.mock('pinia', () => ({
  defineStore: (id, def) => {
    const store = typeof def === 'function' ? def() : def
    return () => ({
      ...store.state?.(),
      ...store.actions,
      ...store.getters,
    })
  },
}))

const { isEventVerified, markEventVerified, getActiveEvent, clearVerifiedEvents } = await import('../auth.js')

describe('auth.js utilities', () => {
  beforeEach(() => {
    sessionStorageMock.clear()
    localStorageMock.clear()
  })

  describe('isEventVerified', () => {
    it('returns false when nothing stored', () => {
      expect(isEventVerified(1)).toBe(false)
    })

    it('returns false for unverified event id', () => {
      sessionStorageMock.setItem('bes_verified_events', JSON.stringify([2]))
      expect(isEventVerified(1)).toBe(false)
    })

    it('returns true when event id is in verified list', () => {
      sessionStorageMock.setItem('bes_verified_events', JSON.stringify([1, 2]))
      expect(isEventVerified(1)).toBe(true)
    })

    it('coerces string id to number', () => {
      sessionStorageMock.setItem('bes_verified_events', JSON.stringify([5]))
      expect(isEventVerified('5')).toBe(true)
    })
  })

  describe('markEventVerified', () => {
    it('adds event id to verified list', () => {
      markEventVerified(3)
      expect(isEventVerified(3)).toBe(true)
    })

    it('does not duplicate event id', () => {
      markEventVerified(3)
      markEventVerified(3)
      const stored = JSON.parse(sessionStorageMock.getItem('bes_verified_events'))
      expect(stored.filter(id => id === 3)).toHaveLength(1)
    })
  })

  describe('getActiveEvent', () => {
    it('returns null when nothing stored', () => {
      expect(getActiveEvent()).toBeNull()
    })

    it('returns parsed event object', () => {
      sessionStorageMock.setItem('bes_active_event', JSON.stringify({ id: 1, name: 'Fest' }))
      expect(getActiveEvent()).toEqual({ id: 1, name: 'Fest' })
    })

    it('returns null on invalid JSON', () => {
      sessionStorageMock.setItem('bes_active_event', 'not-json')
      expect(getActiveEvent()).toBeNull()
    })
  })

  describe('clearVerifiedEvents', () => {
    it('removes both storage keys', () => {
      sessionStorageMock.setItem('bes_verified_events', '[1]')
      sessionStorageMock.setItem('bes_active_event', '{}')

      clearVerifiedEvents()

      expect(sessionStorageMock.getItem('bes_verified_events')).toBeNull()
      expect(sessionStorageMock.getItem('bes_active_event')).toBeNull()
    })
  })
})
