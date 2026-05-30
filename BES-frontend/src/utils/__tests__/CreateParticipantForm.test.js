import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import CreateParticipantForm from '@/components/CreateParticipantForm.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

vi.mock('@/utils/api', () => ({
  addWalkinToSystem: vi.fn(),
  fetchAllGenres: vi.fn(() => Promise.resolve([
    { genreName: 'Popping', format: '3v3' },
    { genreName: 'Waacking', format: '1v1' },
  ])),
  getAllJudges: vi.fn(() => Promise.resolve({})),
}))

import { addWalkinToSystem } from '@/utils/api'

describe('CreateParticipantForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders stage name and genre fields', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: { show: true, event: 'TestEvent' },
    })
    await wrapper.vm.$nextTick()
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('shows per-genre toggle for team-format genre', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [
          { genreName: 'Popping', format: '3v3' },
          { genreName: 'Waacking', format: '1v1' },
        ],
      },
    })
    await wrapper.vm.$nextTick()

    const poppingCheckbox = wrapper.findAll('input[type="checkbox"]').at(0)
    await poppingCheckbox.setValue(true)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Popping Entry Type')
    expect(wrapper.text()).toContain('3v3')

    const soloBtn = wrapper.findAll('button').filter(b => b.text().includes('Solo'))
    expect(soloBtn.length).toBeGreaterThanOrEqual(1)
  })

  it('submits walk-in with per-genre entry mode', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [
          { genreName: 'Popping', format: '3v3' },
        ],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.name = 'Dancer1'
    const checkbox = wrapper.findAll('input[type="checkbox"]').at(0)
    await checkbox.setValue(true)
    await wrapper.vm.$nextTick()

    wrapper.vm.entryModes['Popping'] = 'solo'
    await wrapper.vm.$nextTick()

    const okBtns = wrapper.findAll('button').filter(b => b.text().includes('OK'))
    if (okBtns.length > 0) {
      await okBtns[0].trigger('click')
    } else {
      wrapper.vm.submitNewEntry()
    }
    await wrapper.vm.$nextTick()

    expect(addWalkinToSystem).toHaveBeenCalledWith(
      'Dancer1', 'TestEvent', 'Popping', '', [], '', 'solo'
    )
  })

  it('shows team name + member fields when team entry mode selected', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [
          { genreName: 'Popping', format: '3v3' },
        ],
      },
    })
    await wrapper.vm.$nextTick()

    const checkbox = wrapper.findAll('input[type="checkbox"]').at(0)
    await checkbox.setValue(true)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Popping Team Name')
    expect(wrapper.text()).toContain('Enter the other 2')
  })
})
