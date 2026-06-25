/**
 * Build pair rounds from a flat participant list.
 *
 * SHOWCASE: iterate 1..maxAuditionNumber, fill gaps with _placeholder slots.
 * BATTLE:   compact sequential pairing over real participants only.
 *           Odd count → last participant folds into previous pair (3-way).
 *
 * @param {Array} participants  Objects with at minimum { auditionNumber }
 * @param {string} pairSubMode  'SHOWCASE' | 'BATTLE'
 * @returns {Array<Array>}      Each inner array is one round (1–3 slots)
 */
export function buildPairs(participants, pairSubMode) {
  const sorted = [...participants].sort((a, b) => a.auditionNumber - b.auditionNumber)
  if (!sorted.length) return []

  if (pairSubMode !== 'BATTLE') {
    const maxNum = sorted[sorted.length - 1].auditionNumber
    const result = []
    for (let i = 1; i <= maxNum; i += 2) {
      result.push([
        sorted.find(p => p.auditionNumber === i) ?? { _placeholder: true, auditionNumber: i },
        sorted.find(p => p.auditionNumber === i + 1) ?? { _placeholder: true, auditionNumber: i + 1 },
      ])
    }
    return result
  }

  // BATTLE: compact sequential pairing
  const rounds = []
  for (let i = 0; i < sorted.length; i += 2) {
    rounds.push([sorted[i], sorted[i + 1]].filter(Boolean))
  }
  // Odd count ≥ 3:
  //   N == 3  → rounds is [[a,b],[c]]. Reshape to [[a],[b,c]]:
  //             split the first pair so b joins the lone c, leaving a solo.
  //   N ≥ 5  → fold the last lone into the previous pair → 3-way.
  if (sorted.length % 2 !== 0 && rounds.length >= 2) {
    if (sorted.length === 3) {
      const [a, b] = rounds[0]
      const [c] = rounds[1]
      rounds.splice(0, 2, [a], [b, c])
    } else {
      const lone = rounds.pop()[0]
      rounds[rounds.length - 1].push(lone)
    }
  }
  return rounds
}

/**
 * Map a slot's index within its round to a position label.
 * For 2-way: LEFT | RIGHT
 * For 3-way: LEFT | MIDDLE | RIGHT
 *
 * @param {number} slotIndex   0-based index within the round array
 * @param {number} roundLength 2 or 3
 * @returns {'LEFT'|'MIDDLE'|'RIGHT'}
 */
export function getPositionLabel(slotIndex, roundLength) {
  if (roundLength === 3) {
    if (slotIndex === 0) return 'LEFT'
    if (slotIndex === 1) return 'MIDDLE'
    return 'RIGHT'
  }
  return slotIndex === 0 ? 'LEFT' : 'RIGHT'
}
