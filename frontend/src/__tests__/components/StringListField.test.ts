import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import StringListField from '../../components/StringListField.vue'

function mountWithModel(initial: string[]) {
  const model = ref([...initial])
  const wrapper = mount(StringListField, {
    props: {
      label: 'Non-Goals',
      placeholder: 'e.g. no SSO',
      addLabel: '+ Add',
      modelValue: model.value,
      'onUpdate:modelValue': (v: string[]) => { model.value = v },
    },
  })
  return { wrapper, model }
}

describe('StringListField', () => {
  it('shows empty-state text when list is empty', () => {
    const { wrapper } = mountWithModel([])
    expect(wrapper.text()).toContain('No non-goals yet.')
  })

  it('renders an input for each item', () => {
    const { wrapper } = mountWithModel(['first', 'second'])
    expect(wrapper.findAll('input')).toHaveLength(2)
  })

  it('adds an empty item when the add button is clicked', async () => {
    const { wrapper, model } = mountWithModel(['existing'])
    await wrapper.find('.btn-add').trigger('click')
    expect(model.value).toHaveLength(2)
    expect(model.value[1]).toBe('')
  })

  it('removes the correct item when remove is clicked', async () => {
    const { wrapper, model } = mountWithModel(['keep', 'remove-me'])
    const removeButtons = wrapper.findAll('.btn-remove')
    await removeButtons[1].trigger('click')
    expect(model.value).toHaveLength(1)
    expect(model.value[0]).toBe('keep')
  })

  it('shows the label', () => {
    const { wrapper } = mountWithModel([])
    expect(wrapper.find('label').text()).toBe('Non-Goals')
  })
})
