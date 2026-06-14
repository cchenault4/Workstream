import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StringList from '../../components/StringList.vue'

describe('StringList', () => {
  it('renders nothing when items array is empty', () => {
    const wrapper = mount(StringList, { props: { heading: 'Non-Goals', items: [] } })
    expect(wrapper.find('.subsection').exists()).toBe(false)
  })

  it('renders the heading when items are present', () => {
    const wrapper = mount(StringList, {
      props: { heading: 'Non-Goals', items: ['No SSO', 'No mobile'] },
    })
    expect(wrapper.find('h3').text()).toBe('Non-Goals')
  })

  it('renders one list item per entry', () => {
    const items = ['First item', 'Second item', 'Third item']
    const wrapper = mount(StringList, { props: { heading: 'Assumptions', items } })
    const lis = wrapper.findAll('li')
    expect(lis).toHaveLength(3)
    expect(lis[0].text()).toBe('First item')
    expect(lis[2].text()).toBe('Third item')
  })
})
