// Hand-authored lightning bolt paths in a 0–100 viewBox.
// All paths are OPEN polylines (no Z) so they read as strokes, not shapes.
// Rendered with layered strokes: thick colored glow → medium white → thin white core.
//
// Group A (STRIKE_BOLTS): horizontal left→right screen crosses for impact moments.
// Group B (VERT_BOLTS): vertical top→bottom for falling strikes / corner convergence.
// Group C (BRANCH_BOLTS): short secondary branches that split off a main bolt.

export const STRIKE_BOLTS = [
  'M0 48 L9 41 L15 54 L25 40 L34 53 L42 37 L50 51 L59 38 L67 55 L76 40 L83 52 L91 43 L100 49',
  'M0 52 L8 44 L17 57 L27 43 L35 56 L44 39 L53 53 L62 41 L70 57 L78 42 L86 55 L94 45 L100 50',
  'M0 45 L11 38 L18 52 L29 37 L38 50 L46 36 L54 49 L63 35 L72 52 L80 38 L88 51 L96 42 L100 47',
  'M0 55 L10 47 L19 60 L28 46 L37 59 L46 43 L55 57 L64 44 L72 58 L81 45 L89 57 L97 48 L100 53',
]

export const VERT_BOLTS = [
  'M48 0 L42 11 L56 18 L40 31 L54 40 L40 52 L56 61 L41 72 L55 82 L42 91 L50 100',
  'M52 0 L58 12 L44 21 L57 33 L43 43 L57 54 L42 64 L56 74 L41 85 L54 93 L48 100',
  'M46 0 L54 14 L41 23 L55 35 L41 46 L55 56 L40 67 L54 78 L40 87 L52 95 L46 100',
  'M54 0 L46 13 L58 22 L44 34 L57 44 L43 55 L57 65 L43 76 L56 86 L44 94 L52 100',
]

// Short branches (20–35 units long) designed to attach mid-bolt.
// Rendered alongside a main bolt starting at roughly x=30–50.
export const BRANCH_BOLTS = [
  'M30 45 L38 38 L44 48 L52 36',
  'M35 50 L42 42 L50 53 L58 41',
  'M40 42 L47 35 L55 45 L62 33',
  'M45 55 L52 46 L60 57 L66 44',
]

export const BOLT_PATHS = [...STRIKE_BOLTS, ...VERT_BOLTS]

export const randomStrike = () => STRIKE_BOLTS[Math.floor(Math.random() * STRIKE_BOLTS.length)]
export const randomVert   = () => VERT_BOLTS[Math.floor(Math.random() * VERT_BOLTS.length)]
export const randomBranch = () => BRANCH_BOLTS[Math.floor(Math.random() * BRANCH_BOLTS.length)]
export const randomBolt   = () => BOLT_PATHS[Math.floor(Math.random() * BOLT_PATHS.length)]
