import { describe, it, expect } from 'vitest'
import { barHeightPct, findScoreGainers } from '../smokeChartHelpers'

describe('barHeightPct', () => {
  it('returns 0 for score 0', () => {
    expect(barHeightPct(0)).toBe(0)
  })
  it('returns 100 for score 7', () => {
    expect(barHeightPct(7)).toBe(100)
  })
  it('returns correct percentage for mid scores', () => {
    expect(barHeightPct(3)).toBeCloseTo(42.857, 2)
    expect(barHeightPct(4)).toBeCloseTo(57.143, 2)
  })
  it('clamps to 100 if score exceeds 7', () => {
    expect(barHeightPct(8)).toBe(100)
  })
})

describe('findScoreGainers', () => {
  it('returns names whose score increased', () => {
    const prev = [{ name: 'A', score: 2 }, { name: 'B', score: 1 }]
    const next = [{ name: 'A', score: 3 }, { name: 'B', score: 1 }]
    expect(findScoreGainers(prev, next)).toEqual(['A'])
  })
  it('returns empty array when no scores changed', () => {
    const participants = [{ name: 'A', score: 2 }, { name: 'B', score: 1 }]
    expect(findScoreGainers(participants, participants)).toEqual([])
  })
  it('handles new participant with no previous score', () => {
    const prev = [{ name: 'A', score: 1 }]
    const next = [{ name: 'A', score: 1 }, { name: 'B', score: 1 }]
    expect(findScoreGainers(prev, next)).toEqual(['B'])
  })
  it('returns multiple gainers', () => {
    const prev = [{ name: 'A', score: 1 }, { name: 'B', score: 1 }]
    const next = [{ name: 'A', score: 2 }, { name: 'B', score: 2 }]
    expect(findScoreGainers(prev, next)).toEqual(['A', 'B'])
  })
})
