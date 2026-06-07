import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BattleTimer from '@/components/BattleTimer.vue'

const mockStompClient = {
  connected: true,
  publish: vi.fn(),
  subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })),
  onConnect: null
}

describe('BattleTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders preset buttons when idle and phase is LOCKED', () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    expect(wrapper.text()).toContain('30s')
    expect(wrapper.text()).toContain('45s')
    expect(wrapper.text()).toContain('60s')
    expect(wrapper.text()).toContain('90s')
  })

  it('starts countdown when preset is tapped', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    expect(wrapper.find('.countdown-display').exists()).toBe(true)
    expect(wrapper.find('.countdown-display').text()).toMatch(/\d:\d{2}/)
  })

  it('sends timer state via REST on start and each tick', async () => {
    const fetchSpy = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchSpy)
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    vi.advanceTimersByTime(3000)
    const calls = fetchSpy.mock.calls
    expect(calls.length).toBeGreaterThanOrEqual(3)
    const body = JSON.parse(calls[0][1].body)
    expect(body).toHaveProperty('running')
    expect(body).toHaveProperty('timeLeft')
    expect(body).toHaveProperty('totalDuration')
    vi.unstubAllGlobals()
  })

  it('does NOT auto-unlock at 10s — timer runs independently', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    // Advance 20 seconds (to timeLeft=10) — no unlock emit
    vi.advanceTimersByTime(20000)
    expect(wrapper.emitted('unlock')).toBeFalsy()
  })

  it('restarts timer when a preset is tapped while running', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s to start
    vi.advanceTimersByTime(5000)
    await wrapper.vm.$nextTick()
    await wrapper.findAll('button')[1].trigger('click') // tap 45s to restart
    expect(wrapper.find('.countdown-display').exists()).toBe(true)
    expect(wrapper.find('.countdown-display').text()).toBe('0:45')
  })

  it('keeps counting when phase transitions from LOCKED to VOTING', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    vi.advanceTimersByTime(5000)
    await wrapper.setProps({ phase: 'VOTING' })
    // Timer should STILL be running — it keeps counting to 0
    expect(wrapper.find('.countdown-display').exists()).toBe(true)
  })

  it('stops at 0 with burst animation, then resets to idle', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    vi.advanceTimersByTime(30000) // all the way to 0
    await wrapper.vm.$nextTick()
    // Should show FINISHED burst (TIME text)
    expect(wrapper.text()).toContain('TIME')
    // After burst animation (800ms), should reset to idle
    vi.advanceTimersByTime(800)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.countdown-display').exists()).toBe(false)
    expect(wrapper.text()).toContain('30s') // preset buttons back
  })

  it('shows warning state when timeLeft <= 10', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    vi.advanceTimersByTime(21000) // 9s remaining
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.countdown-display').classes()).toContain('text-red-500')
  })

  it('is muted when phase is not LOCKED and idle', () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'IDLE', stompClient: mockStompClient }
    })
    expect(wrapper.find('.opacity-40').exists()).toBe(true)
  })

  it('finishes at 0 with burst animation, no unlock emit', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.findAll('button')[0].trigger('click') // tap 30s preset
    // Advance full 30s
    vi.advanceTimersByTime(30000)
    // Timer should emit no unlock events
    expect(wrapper.emitted('unlock')).toBeFalsy()
  })
})
