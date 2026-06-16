import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import CreateParticipantForm from '@/components/CreateParticipantForm.vue'

const mockFetch = vi.fn()
global.fetch = mockFetch

vi.mock('@/utils/api', () => ({
  addWalkinToSystem: vi.fn(),
  fetchAllCategories: vi.fn(() => Promise.resolve([
    { categoryName: 'Popping', format: '3v3' },
    { categoryName: 'Waacking', format: '1v1' },
  ])),
  getAllJudges: vi.fn(() => Promise.resolve({})),
}))

import { addWalkinToSystem } from '@/utils/api'

describe('CreateParticipantForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('renders stage name input and category chips when open', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventCategories: [
          { name: 'Popping', format: '3v3' },
          { name: 'Waacking', format: '1v1' },
        ],
      },
    })
    await wrapper.vm.$nextTick()
    // Stage name input (teleported into document.body)
    expect(document.body.querySelector('input[type="text"]')).not.toBeNull()
    // Category chips rendered as buttons (not checkboxes)
    expect(document.body.querySelector('input[type="checkbox"]')).toBeNull()
    const categoryButtons = Array.from(document.body.querySelectorAll('button')).filter(b => b.textContent.includes('Popping'))
    expect(categoryButtons.length).toBeGreaterThan(0)
  })

  it('toggleCategory adds and removes category from createTable.categories', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventCategories: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.categories).toEqual([])

    wrapper.vm.toggleCategory('Popping')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.categories).toContain('Popping')

    wrapper.vm.toggleCategory('Popping')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.categories).not.toContain('Popping')
  })

  it('shows inline team section when a team-format category chip is toggled on', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventCategories: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.toggleCategory('Popping')
    await wrapper.vm.$nextTick()

    // Section rule label for the category (teleported into document.body)
    expect(document.body.textContent).toContain('Popping')
    // Team/Solo toggle buttons appear
    const teamBtn = Array.from(document.body.querySelectorAll('button')).filter(b => b.textContent.trim() === 'Team')
    expect(teamBtn.length).toBeGreaterThan(0)
  })

  it('shows team name and member inputs when team mode active', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventCategories: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.toggleCategory('Popping')
    await wrapper.vm.$nextTick()

    // entryModes defaults to 'team' for team-format category
    expect(wrapper.vm.entryModes['Popping']).toBe('team')
    // Team name input and member inputs present (teleported into document.body)
    const inputs = document.body.querySelectorAll('input[type="text"]')
    // stage name + team name + 2 members = 4
    expect(inputs.length).toBe(4)
  })

  it('submits walk-in with correct args (solo mode)', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventCategories: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.name = 'Dancer1'
    wrapper.vm.toggleCategory('Popping')
    await wrapper.vm.$nextTick()

    wrapper.vm.entryModes['Popping'] = 'solo'
    await wrapper.vm.$nextTick()

    wrapper.vm.submitNewEntry()
    await wrapper.vm.$nextTick()

    expect(addWalkinToSystem).toHaveBeenCalledWith(
      'Dancer1', 'TestEvent', 'Popping', '', [], '', 'solo'
    )
  })
})
