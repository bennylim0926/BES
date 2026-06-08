import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LiveMatchPanel from '@/components/LiveMatchPanel.vue'

const defaultProps = {
  selectedEvent: 'Test Event',
  selectedGenre: 'Hip Hop',
  uniqueGenres: ['Hip Hop', 'Popping'],
  battlePhase: 'IDLE',
  battleJudges: [],
  currentBattle: [],
  currentWinner: -2,
  currentRound: 0,
  currentTop: 'Top16',
  rounds: {},
  topSize: 16,
  isSmoke: false,
  roundNames: ['Top8', 'Top4', 'Top2'],
  saveStatus: 'idle',
  finalTieBlocked: false,
  isReadonly: false,
  genreChampions: {},
  revealActive: false
}

describe('LiveMatchPanel', () => {
  it('renders genre switcher', () => {
    const wrapper = mount(LiveMatchPanel, { props: defaultProps })
    expect(wrapper.text()).toContain('Hip Hop')
    expect(wrapper.text()).toContain('Popping')
  })

  it('renders phase badge', () => {
    const wrapper = mount(LiveMatchPanel, { props: defaultProps })
    expect(wrapper.text()).toContain('Standby')
  })

  it('shows role guidance when readonly (Emcee)', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, isReadonly: true }
    })
    expect(wrapper.text()).toContain('control the battle flow')
  })

  it('hides role guidance when not readonly', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, isReadonly: false }
    })
    expect(wrapper.text()).not.toContain('control the battle flow')
  })

  it('renders OPEN VOTING button in LOCKED phase', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, battlePhase: 'LOCKED', currentBattle: [{}] }
    })
    expect(wrapper.text()).toContain('Start Judging')
  })

  it('renders GET SCORE button in VOTING phase (non-final)', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, battlePhase: 'VOTING', currentBattle: [{ isFinal: false }] }
    })
    expect(wrapper.text()).toContain('Reveal Result')
  })

  it('renders NEXT PAIR button in REVEALED phase', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, battlePhase: 'REVEALED', currentBattle: [{}] }
    })
    expect(wrapper.text()).toContain('Next')
  })

  it('renders round tabs', () => {
    const wrapper = mount(LiveMatchPanel, { props: defaultProps })
    expect(wrapper.text()).toContain('TOP 8')
    expect(wrapper.text()).toContain('TOP 4')
    expect(wrapper.text()).toContain('TOP 2')
  })

  it('renders judge vote grid when not IDLE', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: {
        ...defaultProps,
        battlePhase: 'VOTING',
        battleJudges: [{ id: 1, name: 'Judge A', weightage: 1 }]
      }
    })
    expect(wrapper.text()).toContain('JUDGES')
    expect(wrapper.text()).toContain('Judge A')
  })
})
