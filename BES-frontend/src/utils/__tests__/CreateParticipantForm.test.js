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
    document.body.innerHTML = ''
  })

  it('renders stage name input and genre chips when open', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [
          { name: 'Popping', format: '3v3' },
          { name: 'Waacking', format: '1v1' },
        ],
      },
    })
    await wrapper.vm.$nextTick()
    // Stage name input (teleported into document.body)
    expect(document.body.querySelector('input[type="text"]')).not.toBeNull()
    // Genre chips rendered as buttons (not checkboxes)
    expect(document.body.querySelector('input[type="checkbox"]')).toBeNull()
    const genreButtons = Array.from(document.body.querySelectorAll('button')).filter(b => b.textContent.includes('Popping'))
    expect(genreButtons.length).toBeGreaterThan(0)
  })

  it('toggleGenre adds and removes genre from createTable.genres', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.genres).toEqual([])

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.genres).toContain('Popping')

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.genres).not.toContain('Popping')
  })

  it('shows inline team section when a team-format genre chip is toggled on', async () => {
    const wrapper = mount(CreateParticipantForm, {
      attachTo: document.body,
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()

    // Section rule label for the genre (teleported into document.body)
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
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()

    // entryModes defaults to 'team' for team-format genre
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
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.name = 'Dancer1'
    wrapper.vm.toggleGenre('Popping')
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
