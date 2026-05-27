/**
 * Returns bar height as a percentage (0–100) for a given score out of 7.
 * Clamped to 100 so bars never overflow their container.
 */
export const barHeightPct = (score) => Math.min((score / 7) * 100, 100)

/**
 * Given two snapshots of participants, returns the names of participants
 * whose score increased between snapshots. Used to trigger score pop animation.
 */
export const findScoreGainers = (prevParticipants, nextParticipants) => {
  const prevMap = Object.fromEntries(prevParticipants.map(p => [p.name, p.score]))
  return nextParticipants
    .filter(n => n.score > (prevMap[n.name] ?? 0))
    .map(n => n.name)
}
