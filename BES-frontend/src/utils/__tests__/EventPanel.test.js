import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EventPanel from '@/components/EventPanel.vue'

vi.mock('@/utils/api', () => ({
  fetchAllEvents: vi.fn().mockResolvedValue([
    { id: 1, name: 'Dance Battle 2025' },
    { id: 2, name: 'Street Jam Vol.3' },
  ]),
}))

vi.mock('@/utils/auth', () => ({
  setActiveEvent: vi.fn(),
}))

const activeEvent = { id: 1, name: 'Dance Battle 2025', folderID: null }

function mountPanel(role) {
  return mount(EventPanel, {
    props: { role, activeEvent },
  })
}

describe('EventPanel.vue — section tiles', () => {
  describe('Admin role', () => {
    it('shows all 8 tiles', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tiles = w.findAll('[data-tile]')
      expect(tiles).toHaveLength(8)
    })

    it('shows Details, Audition, Participants, Score, Battle, Numbers tiles', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const labels = w.findAll('[data-tile]').map(t => t.text())
      expect(labels).toEqual(
        expect.arrayContaining(['Details', 'Audition', 'Participants', 'Score', 'Battle', 'Numbers'])
      )
    })
  })

  describe('Organiser role', () => {
    it('shows all 6 tiles (audition hidden)', async () => {
      const w = mountPanel('ROLE_ORGANISER')
      await w.vm.$nextTick()
      expect(w.findAll('[data-tile]')).toHaveLength(6)
    })
  })

  describe('Emcee role', () => {
    it('shows only Audition and Score tiles', async () => {
      const w = mountPanel('ROLE_EMCEE')
      await w.vm.$nextTick()
      const labels = w.findAll('[data-tile]').map(t => t.text())
      expect(labels).toEqual(expect.arrayContaining(['Audition', 'Score']))
      expect(labels).not.toContain('Details')
      expect(labels).not.toContain('Battle')
      expect(labels).not.toContain('Participants')
      expect(labels).not.toContain('Numbers')
    })
  })

  describe('Judge role', () => {
    it('shows only Audition and Battle tiles', async () => {
      const w = mountPanel('ROLE_JUDGE')
      await w.vm.$nextTick()
      const labels = w.findAll('[data-tile]').map(t => t.text())
      expect(labels).toEqual(expect.arrayContaining(['Audition', 'Battle']))
      expect(labels).not.toContain('Score')
      expect(labels).not.toContain('Details')
      expect(labels).not.toContain('Participants')
    })
  })

  describe('tile interactions', () => {
    it('emits navigate with correct route when Audition tile clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tile = w.findAll('[data-tile]').find(t => t.text().includes('Audition'))
      await tile.trigger('click')
      expect(w.emitted('navigate')).toEqual([['Audition List']])
      expect(w.emitted('close')).toBeTruthy()
    })

    it('emits navigate with Score route when Score tile clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tile = w.findAll('[data-tile]').find(t => t.text().includes('Score'))
      await tile.trigger('click')
      expect(w.emitted('navigate')).toEqual([['Score']])
    })

    it('emits goToEventDetails (not navigate) when Details tile clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tile = w.findAll('[data-tile]').find(t => t.text().includes('Details'))
      await tile.trigger('click')
      expect(w.emitted('goToEventDetails')).toBeTruthy()
      expect(w.emitted('navigate')).toBeFalsy()
      expect(w.emitted('close')).toBeTruthy()
    })

    it('emits close when header close button clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      await w.find('[data-close]').trigger('click')
      expect(w.emitted('close')).toBeTruthy()
    })
  })
})

describe('EventPanel.vue — zones', () => {
  it('Admin sees Manage Events zone', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await w.vm.$nextTick()
    expect(w.text()).toContain('Manage Events')
    expect(w.text()).not.toContain('Change Event')
  })

  it('Organiser sees Manage Events zone', async () => {
    const w = mountPanel('ROLE_ORGANISER')
    await w.vm.$nextTick()
    expect(w.text()).toContain('Manage Events')
  })

  it('Emcee sees neither Change Event nor Manage Events (session role — locked event)', async () => {
    const w = mountPanel('ROLE_EMCEE')
    await w.vm.$nextTick()
    expect(w.text()).not.toContain('Change Event')
    expect(w.text()).not.toContain('Manage Events')
  })

  it('Judge sees neither Change Event nor Manage Events (session role — locked event)', async () => {
    const w = mountPanel('ROLE_JUDGE')
    await w.vm.$nextTick()
    expect(w.text()).not.toContain('Change Event')
    expect(w.text()).not.toContain('Manage Events')
  })

  it('Admin panel lists fetched events', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    expect(w.text()).toContain('Dance Battle 2025')
    expect(w.text()).toContain('Street Jam Vol.3')
  })

  it('active event is marked with accent class', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const activeRow = w.findAll('button').find(b => b.text().includes('Dance Battle 2025'))
    expect(activeRow?.classes()).toContain('text-accent')
  })

  it('does not show Change Event for session roles (locked identity)', async () => {
    const w = mountPanel('ROLE_EMCEE')
    await w.vm.$nextTick()
    const btn = w.findAll('button').find(b => b.text().includes('Change Event'))
    expect(btn).toBeUndefined()
  })

  it('emits goToAllEvents and close when All Events clicked', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const btn = w.findAll('button').find(b => b.text().includes('All Events'))
    await btn.trigger('click')
    expect(w.emitted('goToAllEvents')).toBeTruthy()
    expect(w.emitted('close')).toBeTruthy()
  })

  it('calls setActiveEvent when an event in the list is clicked', async () => {
    const { setActiveEvent } = await import('@/utils/auth')
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const row = w.findAll('button').find(b => b.text().includes('Street Jam Vol.3'))
    await row.trigger('click')
    expect(setActiveEvent).toHaveBeenCalledWith(2, 'Street Jam Vol.3')
  })

  it('emits close after switching event', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const row = w.findAll('button').find(b => b.text().includes('Street Jam Vol.3'))
    await row.trigger('click')
    expect(w.emitted('close')).toBeTruthy()
  })
})
