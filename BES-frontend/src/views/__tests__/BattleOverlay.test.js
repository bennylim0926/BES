import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'

vi.mock('@/utils/api', () => ({
  getBattleJudges: vi.fn().mockResolvedValue({ judges: [] }),
  getCurrentBattlePair: vi.fn().mockResolvedValue(null),
  getImage: vi.fn().mockResolvedValue(null),
  getOverlayConfig: vi.fn().mockResolvedValue({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }),
}))
vi.mock('@/utils/websocket', () => ({
  createClient: vi.fn().mockReturnValue({ value: null }),
  deactivateClient: vi.fn(),
  subscribeToChannel: vi.fn(),
}))
vi.mock('../Chart.vue', () => ({ default: { template: '<div />' } }))

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/battle/overlay', component: { template: '<div />' } }],
})

const BattleOverlay = (await import('../BattleOverlay.vue')).default

describe('BattleOverlay.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.documentElement.classList.remove('transparent-page')
  })

  it('mounts without errors', async () => {
    const wrapper = mount(BattleOverlay, {
      global: { plugins: [router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('adds transparent-page class to html on mount', async () => {
    mount(BattleOverlay, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    expect(document.documentElement.classList.contains('transparent-page')).toBe(true)
  })

  it('renders .overlay-root element', async () => {
    const wrapper = mount(BattleOverlay, { global: { plugins: [router] } })
    expect(wrapper.find('.overlay-root').exists()).toBe(true)
  })
})
