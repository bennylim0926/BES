import { describe, it, expect } from 'vitest'
import { OVERLAY_THEMES, resolveTheme, deriveRoundLabel } from '../overlayThemes'

describe('OVERLAY_THEMES', () => {
  it('has impact, hype, lightning entries', () => {
    expect(OVERLAY_THEMES.impact).toBeDefined()
    expect(OVERLAY_THEMES.hype).toBeDefined()
    expect(OVERLAY_THEMES.lightning).toBeDefined()
  })

  it('impact has correct defaults', () => {
    const t = OVERLAY_THEMES.impact
    expect(t.label).toBe('Impact')
    expect(t.legacy).toBe(true)
    expect(t.accent).toBe('#ffffff')
    expect(t.timing.vsShakeDelay).toBe(340)
    expect(t.timing.votePause).toBe(1500)
    expect(t.timing.finalPause).toBe(400)
    expect(t.timing.secondShake).toBe(false)
    expect(t.timing.titleTotal).toBe(0)
    expect(t.timing.vsDelay).toBe(0)
    expect(t.timing.finalsExtra).toBe(0)
  })

  it('hype has secondShake true', () => {
    expect(OVERLAY_THEMES.hype.timing.secondShake).toBe(true)
    expect(OVERLAY_THEMES.hype.timing.votePause).toBe(2500)
  })

  it('lightning has correct accent and timing', () => {
    const t = OVERLAY_THEMES.lightning
    expect(t.legacy).toBe(false)
    expect(t.accent).toBe('#00d4ff')
    expect(t.timing.titleTotal).toBe(2400)
    expect(t.timing.vsDelay).toBe(500)
    expect(t.timing.finalsExtra).toBe(600)
  })
})

describe('resolveTheme', () => {
  it('returns impact theme for "impact"', () => {
    expect(resolveTheme('impact')).toBe(OVERLAY_THEMES.impact)
  })

  it('returns hype theme for "hype"', () => {
    expect(resolveTheme('hype')).toBe(OVERLAY_THEMES.hype)
  })

  it('returns lightning theme for "lightning"', () => {
    const theme = resolveTheme('lightning')
    expect(theme.accent).toBe('#00d4ff')
  })

  it('returns impact for unknown key', () => {
    expect(resolveTheme('disco')).toBe(OVERLAY_THEMES.impact)
  })

  it('returns impact for undefined key', () => {
    expect(resolveTheme(undefined)).toBe(OVERLAY_THEMES.impact)
  })

  it('returns impact for null key', () => {
    expect(resolveTheme(null)).toBe(OVERLAY_THEMES.impact)
  })
})

describe('deriveRoundLabel', () => {
  it('returns empty for smoke mode', () => {
    expect(deriveRoundLabel({ isSmoke: true })).toBe('')
  })

  it('returns GRAND FINAL when isFinal true', () => {
    const result = deriveRoundLabel({
      rounds: { Top16: [['A', 'B', '']] },
      leftName: 'A',
      rightName: 'B',
      isFinal: true,
      isSmoke: false,
    })
    expect(result).toBe('GRAND FINAL')
  })

  it('returns GRAND FINAL even when smoke is true but isFinal takes priority', () => {
    const result = deriveRoundLabel({
      isFinal: true,
      isSmoke: true,
    })
    expect(result).toBe('GRAND FINAL')
  })

  it('returns empty when rounds is missing', () => {
    expect(deriveRoundLabel({ leftName: 'A', rightName: 'B' })).toBe('')
  })

  it('returns empty when leftName is missing', () => {
    expect(deriveRoundLabel({ rounds: {} })).toBe('')
  })

  it('returns empty when rightName is missing', () => {
    expect(deriveRoundLabel({ rounds: {}, leftName: 'A' })).toBe('')
  })

  it('returns empty when no params passed', () => {
    expect(deriveRoundLabel()).toBe('')
  })

  it('scans rounds and returns TOP N for matching pair', () => {
    const result = deriveRoundLabel({
      rounds: {
        Top16: [['A', 'B', '']],
      },
      leftName: 'A',
      rightName: 'B',
      isFinal: false,
      isSmoke: false,
    })
    expect(result).toBe('TOP 16')
  })

  it('scans multiple round keys and returns first match', () => {
    const result = deriveRoundLabel({
      rounds: {
        Top8: [['X', 'Y', '']],
        Top4: [['A', 'B', '']],
      },
      leftName: 'A',
      rightName: 'B',
      isFinal: false,
      isSmoke: false,
    })
    expect(result).toBe('TOP 4')
  })

  it('returns empty when no matching pair found', () => {
    const result = deriveRoundLabel({
      rounds: {
        Top8: [['X', 'Y', '']],
      },
      leftName: 'A',
      rightName: 'B',
      isFinal: false,
      isSmoke: false,
    })
    expect(result).toBe('')
  })

  it('ignores keys that do not match TopN pattern', () => {
    const result = deriveRoundLabel({
      rounds: {
        Finals: [['A', 'B', '']],
      },
      leftName: 'A',
      rightName: 'B',
      isFinal: false,
      isSmoke: false,
    })
    expect(result).toBe('')
  })

  it('ignores keys whose value is not an array', () => {
    const result = deriveRoundLabel({
      rounds: {
        Top8: 'not-an-array',
      },
      leftName: 'A',
      rightName: 'B',
      isFinal: false,
      isSmoke: false,
    })
    expect(result).toBe('')
  })
})
