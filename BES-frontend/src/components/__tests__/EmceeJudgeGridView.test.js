import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmceeJudgeGridView from '../EmceeJudgeGridView.vue'

const judgeSlots = {
  Jay: [
    { auditionNumber: 1, participantName: 'Alice', memberNames: [] },
    { auditionNumber: 2, participantName: 'Dave',  memberNames: [] },
  ],
  Kay: [
    { auditionNumber: 1, participantName: 'Bob',   memberNames: [] },
  ]
}

describe('EmceeJudgeGridView', () => {
  it('renders one column header per judge', () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    expect(wrapper.text()).toContain('Jay')
    expect(wrapper.text()).toContain('Kay')
  })

  it('shows participant at current round for each judge', () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Bob')
  })

  it('shows em-dash for judge with no participant at current round', () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 2, maxRound: 2 }
    })
    expect(wrapper.text()).toContain('Dave')
    expect(wrapper.text()).toContain('—')  // Kay has no round 2
  })

  it('emits advance on NEXT click', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    await wrapper.find('[data-testid="next-round"]').trigger('click')
    expect(wrapper.emitted('advance')).toBeTruthy()
  })

  it('emits retreat on PREV click', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 2, maxRound: 2 }
    })
    await wrapper.find('[data-testid="prev-round"]').trigger('click')
    expect(wrapper.emitted('retreat')).toBeTruthy()
  })

  it('PREV is disabled at round 1', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    const prevBtn = wrapper.find('[data-testid="prev-round"]')
    expect(prevBtn.attributes('disabled')).toBeDefined()
  })

  it('NEXT is disabled at maxRound', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 2, maxRound: 2 }
    })
    const nextBtn = wrapper.find('[data-testid="next-round"]')
    expect(nextBtn.attributes('disabled')).toBeDefined()
  })
})
