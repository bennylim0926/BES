import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

beforeEach(() => {
  vi.useFakeTimers()
  vi.stubGlobal('matchMedia', vi.fn())
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

function mockMatchMedia(reduced) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query) => ({
      matches: query === '(prefers-reduced-motion: reduce)' ? reduced : false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  )
}

describe('useOverlayParticles', () => {
  describe('normal motion', () => {
    beforeEach(() => {
      mockMatchMedia(false)
    })

    it('spawnAmbient adds particles with ambient=true and unique ids', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, spawnAmbient } = useOverlayParticles()

      spawnAmbient(3, (i) => ({ x: i * 10 }))

      expect(particles.value.length).toBe(3)
      particles.value.forEach((p, i) => {
        expect(p.ambient).toBe(true)
        expect(p.x).toBe(i * 10)
        expect(typeof p.id).toBe('number')
      })
      const ids = particles.value.map((p) => p.id)
      expect(new Set(ids).size).toBe(3)
    })

    it('spawnAmbient multiple times accumulates particles', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, spawnAmbient } = useOverlayParticles()

      spawnAmbient(2, () => ({}))
      spawnAmbient(3, () => ({}))

      expect(particles.value.length).toBe(5)
    })

    it('burst adds transient particles and removes them after TTL', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, burst } = useOverlayParticles()

      burst(2, (i) => ({ label: `b${i}` }), 1000)

      expect(particles.value.length).toBe(2)
      particles.value.forEach((p) => {
        expect(p.ambient).toBe(false)
      })

      vi.advanceTimersByTime(999)
      expect(particles.value.length).toBe(2)

      vi.advanceTimersByTime(1)
      expect(particles.value.length).toBe(0)
    })

    it('burst and ambient coexist — burst clears, ambient stays', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, spawnAmbient, burst } = useOverlayParticles()

      spawnAmbient(2, () => ({ ambient: true }))
      burst(1, () => ({ ambient: false }), 500)

      expect(particles.value.length).toBe(3)

      vi.advanceTimersByTime(500)
      expect(particles.value.length).toBe(2)
      particles.value.forEach((p) => {
        expect(p.ambient).toBe(true)
      })
    })

    it('clear empties particles immediately', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, spawnAmbient, burst, clear } = useOverlayParticles()

      spawnAmbient(2, () => ({}))
      burst(3, () => ({}), 1000)

      clear()
      expect(particles.value.length).toBe(0)
    })

    it('clear cancels pending timers so burst particles never auto-remove', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, spawnAmbient, burst, clear } = useOverlayParticles()

      spawnAmbient(1, () => ({}))
      burst(1, () => ({}), 1000)
      clear()

      particles.value.push({ id: 999, ambient: true })

      vi.advanceTimersByTime(2000)
      expect(particles.value.length).toBe(1)
      expect(particles.value[0].id).toBe(999)
    })
  })

  describe('reduced motion', () => {
    beforeEach(() => {
      mockMatchMedia(true)
    })

    it('spawnAmbient adds zero particles', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, spawnAmbient } = useOverlayParticles()

      spawnAmbient(5, (i) => ({ i }))

      expect(particles.value.length).toBe(0)
    })

    it('burst adds zero particles', () => {
      const { useOverlayParticles } = require('../overlayParticles')
      const { particles, burst } = useOverlayParticles()

      burst(5, (i) => ({ i }), 1000)

      expect(particles.value.length).toBe(0)
    })
  })
})
