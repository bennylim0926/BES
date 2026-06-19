import { describe, it, expect } from 'vitest'
import {
  sortRowsForPool,
  computeNextEligibleAdd,
  computeNextEligibleRemove,
  addedPoolOrdered,
} from '../scoreTiePool.js'

const row = (name, totalScore, auditionNumber) => ({
  participantName: name, totalScore, auditionNumber,
})

describe('sortRowsForPool', () => {
  it('sorts by totalScore DESC', () => {
    const rows = [row('A', 5.0, 1), row('B', 7.0, 2), row('C', 6.0, 3)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['B', 'C', 'A'])
  })

  it('breaks score ties with auditionNumber ASC', () => {
    const rows = [row('A', 7.0, 5), row('B', 7.0, 2), row('C', 7.0, 8)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['B', 'A', 'C'])
  })

  it('falls back to alphabetical name when score AND auditionNumber tie', () => {
    const rows = [row('Charlie', 7.0, 3), row('Alpha', 7.0, 3), row('Bravo', 7.0, 3)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['Alpha', 'Bravo', 'Charlie'])
  })

  it('treats null/undefined auditionNumber as Infinity (sorts last within tied score)', () => {
    const rows = [row('A', 7.0, null), row('B', 7.0, 2), row('C', 7.0, 5)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['B', 'C', 'A'])
  })

  it('does not mutate the input array', () => {
    const rows = [row('A', 5.0, 1), row('B', 7.0, 2)]
    const copy = [...rows]
    sortRowsForPool(rows)
    expect(rows).toEqual(copy)
  })
})

describe('computeNextEligibleAdd', () => {
  const allRows = [
    row('Drift', 7.3, 4),
    row('Echo',  7.3, 18),
    row('Halo',  7.2, 7),
    row('Pulse', 7.2, 22),
    row('Mirage', 7.1, 12),
  ]

  it('returns the highest-score not-yet-included row', () => {
    const result = computeNextEligibleAdd(allRows, new Set(['Drift', 'Echo']))
    expect(result).toEqual({ participantName: 'Halo', totalScore: 7.2, auditionNumber: 7 })
  })

  it('uses auditionNumber ASC to break score ties', () => {
    const result = computeNextEligibleAdd(allRows, new Set(['Drift', 'Echo', 'Halo']))
    expect(result.participantName).toBe('Pulse')
  })

  it('returns null when no eligible rows remain', () => {
    const all = new Set(['Drift', 'Echo', 'Halo', 'Pulse', 'Mirage'])
    expect(computeNextEligibleAdd(allRows, all)).toBeNull()
  })

  it('with maxScore: skips rows at or above the cutoff and returns first below', () => {
    // Tie breaker at 7.3 — Drift & Echo (7.3) are above cutoff, Halo (7.2) is first below
    const included = new Set(['Drift', 'Echo']) // tie base at cutoff
    const result = computeNextEligibleAdd(allRows, included, 7.3)
    expect(result.participantName).toBe('Halo')
    expect(result.totalScore).toBe(7.2)
  })

  it('with maxScore: respects auditionNumber tiebreak among below-cutoff rows', () => {
    // Tie breaker at 7.3 — Halo (#7) and Pulse (#22) are both at 7.2, Halo should come first
    const included = new Set(['Drift', 'Echo'])
    const result = computeNextEligibleAdd(allRows, included, 7.3)
    expect(result.participantName).toBe('Halo')
    expect(result.auditionNumber).toBe(7)
  })

  it('with maxScore: returns null when no one is below the cutoff', () => {
    // Cutoff at 7.0 — everyone is at or above
    const included = new Set()
    expect(computeNextEligibleAdd(allRows, included, 7.0)).toBeNull()
  })
})

describe('computeNextEligibleRemove', () => {
  const allRows = [
    row('Drift', 7.3, 4),
    row('Echo',  7.3, 18),
    row('Halo',  7.2, 7),
    row('Pulse', 7.2, 22),
    row('Mirage', 7.1, 12),
  ]
  const tieBase = new Set(['Drift', 'Echo'])

  it('returns the last (lowest-rank) entry in the added pool', () => {
    const added = new Set(['Halo', 'Pulse', 'Mirage'])
    const result = computeNextEligibleRemove(allRows, tieBase, added)
    expect(result).toEqual({ participantName: 'Mirage', totalScore: 7.1, auditionNumber: 12 })
  })

  it('returns the lowest of a same-score tier when that tier is the last added', () => {
    const added = new Set(['Halo', 'Pulse'])
    const result = computeNextEligibleRemove(allRows, tieBase, added)
    // Halo (#07) ranks above Pulse (#22) within score 7.2, so Pulse is the lowest-rank added
    expect(result.participantName).toBe('Pulse')
  })

  it('returns null when added pool is empty (would touch the locked base)', () => {
    expect(computeNextEligibleRemove(allRows, tieBase, new Set())).toBeNull()
  })
})

describe('addedPoolOrdered', () => {
  const allRows = [
    row('Drift', 7.3, 4),
    row('Halo',  7.2, 7),
    row('Pulse', 7.2, 22),
    row('Mirage', 7.1, 12),
  ]

  it('orders the added members by score DESC, then auditionNumber ASC', () => {
    const result = addedPoolOrdered(allRows, new Set(['Mirage', 'Pulse', 'Halo']))
    expect(result.map(r => r.participantName)).toEqual(['Halo', 'Pulse', 'Mirage'])
  })

  it('returns empty array when set is empty', () => {
    expect(addedPoolOrdered(allRows, new Set())).toEqual([])
  })

  it('silently skips names not present in allRows', () => {
    const result = addedPoolOrdered(allRows, new Set(['Halo', 'Ghost', 'Pulse']))
    expect(result.map(r => r.participantName)).toEqual(['Halo', 'Pulse'])
  })
})
