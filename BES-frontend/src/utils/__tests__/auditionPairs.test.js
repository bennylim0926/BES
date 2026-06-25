import { describe, it, expect } from 'vitest'
import { buildPairs, getPositionLabel } from '../auditionPairs.js'

const p = (n) => ({ auditionNumber: n, participantName: `P${n}` })

describe('buildPairs — SHOWCASE', () => {
  it('returns empty array for no participants', () => {
    expect(buildPairs([], 'SHOWCASE')).toEqual([])
  })

  it('fills gaps with placeholders', () => {
    const result = buildPairs([p(1), p(4)], 'SHOWCASE')
    expect(result).toHaveLength(2)
    expect(result[0][0].auditionNumber).toBe(1)
    expect(result[0][1]).toMatchObject({ _placeholder: true, auditionNumber: 2 })
    expect(result[1][0]).toMatchObject({ _placeholder: true, auditionNumber: 3 })
    expect(result[1][1].auditionNumber).toBe(4)
  })

  it('handles even participant count with no gaps', () => {
    const result = buildPairs([p(1), p(2), p(3), p(4)], 'SHOWCASE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([3, 4])
  })
})

describe('buildPairs — BATTLE', () => {
  it('returns empty array for no participants', () => {
    expect(buildPairs([], 'BATTLE')).toEqual([])
  })

  it('pairs real participants compactly, skipping gaps', () => {
    const result = buildPairs([p(1), p(4)], 'BATTLE')
    expect(result).toHaveLength(1)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 4])
  })

  it('even count: creates n/2 two-way rounds', () => {
    const result = buildPairs([p(1), p(2), p(5), p(6)], 'BATTLE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([5, 6])
  })

  it('odd count ≥ 3: last participant joins previous pair as 3-way', () => {
    const result = buildPairs([p(1), p(2), p(3), p(4), p(5)], 'BATTLE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([3, 4, 5])
  })

  it('odd count = 1: single participant in its own round (no 3-way without a previous pair)', () => {
    const result = buildPairs([p(3)], 'BATTLE')
    expect(result).toHaveLength(1)
    expect(result[0].map(s => s.auditionNumber)).toEqual([3])
  })

  it('no _placeholder slots ever appear in BATTLE mode', () => {
    const result = buildPairs([p(1), p(3), p(7)], 'BATTLE')
    result.flat().forEach(slot => expect(slot._placeholder).toBeFalsy())
  })

  it('sorts by auditionNumber before pairing even if input is unsorted', () => {
    const result = buildPairs([p(5), p(1), p(4), p(2)], 'BATTLE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([4, 5])
  })
})

describe('getPositionLabel', () => {
  it('2-way: index 0 = LEFT, index 1 = RIGHT', () => {
    expect(getPositionLabel(0, 2)).toBe('LEFT')
    expect(getPositionLabel(1, 2)).toBe('RIGHT')
  })

  it('3-way: index 0 = LEFT, index 1 = MIDDLE, index 2 = RIGHT', () => {
    expect(getPositionLabel(0, 3)).toBe('LEFT')
    expect(getPositionLabel(1, 3)).toBe('MIDDLE')
    expect(getPositionLabel(2, 3)).toBe('RIGHT')
  })
})
