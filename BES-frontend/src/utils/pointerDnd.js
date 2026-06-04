/**
 * Parse a data-drop-key attribute value into a typed drop target descriptor.
 *
 * Bracket keys: "bracket-Top8-0-1"  → { type: 'bracket', roundKey: 'Top8', matchIdx: 0, slotIdx: 1 }
 * Smoke keys:   "smoke-3"           → { type: 'smoke', idx: 3 }
 * Anything else: null
 */
export function parseDropKey(key) {
  if (!key) return null

  if (key.startsWith('smoke-')) {
    const idx = parseInt(key.slice(6), 10)
    if (isNaN(idx)) return null
    return { type: 'smoke', idx }
  }

  if (key.startsWith('bracket-')) {
    const inner = key.slice(8)           // e.g. "Top8-0-1"
    const parts = inner.split('-')       // ["Top8", "0", "1"]
    if (parts.length < 3) return null
    const slotIdx  = parseInt(parts[parts.length - 1], 10)
    const matchIdx = parseInt(parts[parts.length - 2], 10)
    const roundKey = parts.slice(0, parts.length - 2).join('-')  // "Top8"
    if (isNaN(slotIdx) || isNaN(matchIdx) || !roundKey) return null
    return { type: 'bracket', roundKey, matchIdx, slotIdx }
  }

  return null
}
