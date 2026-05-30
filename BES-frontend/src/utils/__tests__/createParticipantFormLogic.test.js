import { describe, it, expect } from 'vitest'

function getAdditionalMembersCount(format, entryMode) {
  if (!format || format.toLowerCase() === '1v1' || entryMode === 'solo') return 0
  const match = format.match(/^(\d+)v\d+$/i)
  return match ? parseInt(match[1]) - 1 : 0
}

describe('getAdditionalMembersCount', () => {
  it('returns 0 for 1v1', () => expect(getAdditionalMembersCount('1v1', 'team')).toBe(0))
  it('returns 0 for null format', () => expect(getAdditionalMembersCount(null, 'team')).toBe(0))
  it('returns 0 for solo mode in 2v2', () => expect(getAdditionalMembersCount('2v2', 'solo')).toBe(0))
  it('returns 0 for solo mode in 3v3', () => expect(getAdditionalMembersCount('3v3', 'solo')).toBe(0))
  it('returns 1 for 2v2 team', () => expect(getAdditionalMembersCount('2v2', 'team')).toBe(1))
  it('returns 2 for 3v3 team', () => expect(getAdditionalMembersCount('3v3', 'team')).toBe(2))
  it('returns 3 for 4v4 team', () => expect(getAdditionalMembersCount('4v4', 'team')).toBe(3))
  it('returns 0 for unrecognized format', () => expect(getAdditionalMembersCount('abc', 'team')).toBe(0))
})
