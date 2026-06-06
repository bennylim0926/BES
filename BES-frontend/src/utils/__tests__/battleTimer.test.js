import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BattleTimer from '@/components/BattleTimer.vue'

const mockStompClient = {
  connected: true,
  publish: vi.fn()
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
    expect(wrapper.text()).toContain('START')
  })

  it('starts countdown when START is clicked', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.start-btn').trigger('click')
    expect(wrapper.find('.countdown-display').exists()).toBe(true)
    expect(wrapper.find('.countdown-display').text()).toMatch(/\d:\d{2}/)
  })

  it('broadcasts timer state on start and each tick', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.start-btn').trigger('click')
    vi.advanceTimersByTime(3000)
    const calls = mockStompClient.publish.mock.calls
    expect(calls.length).toBeGreaterThanOrEqual(3)
    const body = JSON.parse(calls[0][0].body)
    expect(body).toHaveProperty('running')
    expect(body).toHaveProperty('timeLeft')
    expect(body).toHaveProperty('totalDuration')
  })

  it('emits unlock event at exactly 10s remaining', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    // Start with 30s
    await wrapper.find('.start-btn').trigger('click')
    // Advance 20 seconds (to timeLeft=10)
    vi.advanceTimersByTime(20000)
    expect(wrapper.emitted('unlock')).toBeTruthy()
    expect(wrapper.emitted('unlock').length).toBe(1)
  })

  it('resets timer when reset button is clicked', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.start-btn').trigger('click')
    vi.advanceTimersByTime(5000)
    await wrapper.find('.reset-btn').trigger('click')
    expect(wrapper.find('.countdown-display').exists()).toBe(false)
    expect(wrapper.text()).toContain('30s') // preset buttons visible
  })

  it('resets timer when phase leaves LOCKED', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.start-btn').trigger('click')
    vi.advanceTimersByTime(5000)
    await wrapper.setProps({ phase: 'VOTING' })
    // Should reset to idle
    expect(wrapper.find('.countdown-display').exists()).toBe(false)
  })

  it('shows warning state when timeLeft <= 10', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.start-btn').trigger('click') // 30s
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

  it('only emits unlock once', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.start-btn').trigger('click')
    vi.advanceTimersByTime(20000) // to 10
    expect(wrapper.emitted('unlock').length).toBe(1)
    vi.advanceTimersByTime(1000) // to 9
    expect(wrapper.emitted('unlock').length).toBe(1) // still 1
  })
})
