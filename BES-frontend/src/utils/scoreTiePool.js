// Pure helpers for the Score.vue tie-resolver pool.
// See docs/superpowers/specs/2026-06-13-score-redesign-design.md §3.5 + §9.3.

const cmp = (a, b) => {
  const sa = b.totalScore - a.totalScore                 // score DESC
  if (sa !== 0) return sa
  const ana = a.auditionNumber == null ? Infinity : a.auditionNumber
  const anb = b.auditionNumber == null ? Infinity : b.auditionNumber
  if (ana !== anb) return ana - anb                      // auditionNumber ASC
  return a.participantName.localeCompare(b.participantName) // name ASC fallback
}

export function sortRowsForPool(rows) {
  return [...rows].sort(cmp)
}

export function computeNextEligibleAdd(allRows, includedNames) {
  const sorted = sortRowsForPool(allRows)
  for (const r of sorted) {
    if (!includedNames.has(r.participantName)) return r
  }
  return null
}

export function computeNextEligibleRemove(allRows, tieBaseNames, addedNames) {
  if (addedNames.size === 0) return null
  // Last-in-order = lowest-ranked member of the added set
  const sorted = sortRowsForPool(allRows).filter(r => addedNames.has(r.participantName))
  return sorted.length ? sorted[sorted.length - 1] : null
}

export function addedPoolOrdered(allRows, addedNames) {
  return sortRowsForPool(allRows).filter(r => addedNames.has(r.participantName))
}
