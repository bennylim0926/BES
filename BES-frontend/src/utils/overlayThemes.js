export const OVERLAY_THEMES = {
  impact: {
    label: 'Impact',
    legacy: true,
    accent: '#ffffff',
    timing: {
      vsShakeDelay: 340,
      votePause: 1500,
      finalPause: 400,
      secondShake: false,
      titleTotal: 0,
      vsDelay: 0,
      finalsExtra: 0,
    },
  },
  hype: {
    label: 'Hype',
    legacy: true,
    accent: '#ffffff',
    timing: {
      vsShakeDelay: 420,
      votePause: 2500,
      finalPause: 800,
      secondShake: true,
      titleTotal: 0,
      vsDelay: 0,
      finalsExtra: 0,
    },
  },
  lightning: {
    label: 'Lightning',
    legacy: false,
    accent: '#00d4ff',
    timing: {
      vsShakeDelay: 340,
      votePause: 1500,
      finalPause: 400,
      secondShake: false,
      titleTotal: 2400,
      vsDelay: 500,
      finalsExtra: 600,
    },
  },
}

export function resolveTheme(key) {
  return OVERLAY_THEMES[key] || OVERLAY_THEMES.impact
}

export function deriveRoundLabel({ rounds, leftName, rightName, isFinal, isSmoke } = {}) {
  if (isFinal) return 'GRAND FINAL'
  if (isSmoke) return ''
  if (!rounds || !leftName || !rightName) return ''

  for (const [key, value] of Object.entries(rounds)) {
    const match = key.match(/^Top(\d+)$/)
    if (!match || !Array.isArray(value)) continue
    const found = value.find((m) => m[0] === leftName && m[1] === rightName)
    if (found) return `TOP ${match[1]}`
  }

  return ''
}
