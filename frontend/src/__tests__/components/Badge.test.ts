import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Badge from '../../components/Badge.vue'

describe('Badge', () => {
  it('renders the label text', () => {
    const wrapper = mount(Badge, { props: { variant: 'badge-high', label: 'HIGH' } })
    expect(wrapper.text()).toBe('HIGH')
  })

  it('applies the variant as a CSS class', () => {
    const wrapper = mount(Badge, { props: { variant: 'badge-blocked', label: 'BLOCKED' } })
    expect(wrapper.classes()).toContain('badge-blocked')
  })

  it('always has the base badge class', () => {
    const wrapper = mount(Badge, { props: { variant: 'badge-new', label: 'NEW' } })
    expect(wrapper.classes()).toContain('badge')
  })

  it('renders as a span', () => {
    const wrapper = mount(Badge, { props: { variant: 'badge-low', label: 'LOW' } })
    expect(wrapper.element.tagName).toBe('SPAN')
  })
})
