import { describe, it, expect } from 'vitest'
import { parseDropKey } from '../pointerDnd'

describe('parseDropKey', () => {
  describe('smoke keys', () => {
    it('parses smoke-0', () => {
      expect(parseDropKey('smoke-0')).toEqual({ type: 'smoke', idx: 0 })
    })
    it('parses smoke-7', () => {
      expect(parseDropKey('smoke-7')).toEqual({ type: 'smoke', idx: 7 })
    })
    it('returns null for malformed smoke key', () => {
      expect(parseDropKey('smoke-')).toBeNull()
      expect(parseDropKey('smoke-abc')).toBeNull()
    })
  })

  describe('bracket keys', () => {
    it('parses Top8 bracket key slot 0', () => {
      expect(parseDropKey('bracket-Top8-0-0')).toEqual({
        type: 'bracket', roundKey: 'Top8', matchIdx: 0, slotIdx: 0,
      })
    })
    it('parses Top8 bracket key slot 1', () => {
      expect(parseDropKey('bracket-Top8-2-1')).toEqual({
        type: 'bracket', roundKey: 'Top8', matchIdx: 2, slotIdx: 1,
      })
    })
    it('parses Top16 bracket key', () => {
      expect(parseDropKey('bracket-Top16-3-0')).toEqual({
        type: 'bracket', roundKey: 'Top16', matchIdx: 3, slotIdx: 0,
      })
    })
    it('parses Top32 bracket key', () => {
      expect(parseDropKey('bracket-Top32-15-1')).toEqual({
        type: 'bracket', roundKey: 'Top32', matchIdx: 15, slotIdx: 1,
      })
    })
    it('returns null for too-short bracket key', () => {
      expect(parseDropKey('bracket-Top8-0')).toBeNull()
    })
  })

  describe('edge cases', () => {
    it('returns null for null input', () => {
      expect(parseDropKey(null)).toBeNull()
    })
    it('returns null for empty string', () => {
      expect(parseDropKey('')).toBeNull()
    })
    it('returns null for unknown prefix', () => {
      expect(parseDropKey('pool-Alice')).toBeNull()
    })
  })
})
