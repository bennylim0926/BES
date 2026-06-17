import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DemoRolePicker from '../DemoRolePicker.vue'

function createWrapper() {
  return mount(DemoRolePicker, {
    global: {
      stubs: { Teleport: true },
    },
  })
}

describe('DemoRolePicker', () => {
  it('renders three role cards', () => {
    const wrapper = createWrapper()
    const cards = wrapper.findAll('.role-card')
    expect(cards).toHaveLength(3)
  })

  it('emits select with EMCEE when Emcee card is clicked', async () => {
    const wrapper = createWrapper()
    const cards = wrapper.findAll('.role-card')
    await cards[0].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0]).toEqual(['EMCEE'])
  })

  it('emits select with JUDGE when Judge card is clicked', async () => {
    const wrapper = createWrapper()
    const cards = wrapper.findAll('.role-card')
    await cards[1].trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual(['JUDGE'])
  })

  it('emits select with HELPER when Helper card is clicked', async () => {
    const wrapper = createWrapper()
    const cards = wrapper.findAll('.role-card')
    await cards[2].trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual(['HELPER'])
  })

  it('emits back when backdrop is clicked', async () => {
    const wrapper = createWrapper()
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('back')).toBeTruthy()
  })

  it('does not emit back when role card is clicked', async () => {
    const wrapper = createWrapper()
    await wrapper.find('.role-card').trigger('click')
    expect(wrapper.emitted('back')).toBeFalsy()
    expect(wrapper.emitted('select')).toBeTruthy()
  })
})
