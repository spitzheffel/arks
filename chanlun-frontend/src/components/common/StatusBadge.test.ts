import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusBadge from './StatusBadge.vue'

describe('StatusBadge', () => {
  it('应该渲染状态文本', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        status: '运行中'
      }
    })
    expect(wrapper.text()).toBe('运行中')
  })

  it('应该应用正确的样式类 - success', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        status: '成功',
        type: 'success'
      }
    })
    expect(wrapper.classes()).toContain('bg-green-100')
    expect(wrapper.classes()).toContain('text-green-800')
  })

  it('应该应用正确的样式类 - danger', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        status: '失败',
        type: 'danger'
      }
    })
    expect(wrapper.classes()).toContain('bg-red-100')
    expect(wrapper.classes()).toContain('text-red-800')
  })

  it('应该应用正确的样式类 - warning', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        status: '警告',
        type: 'warning'
      }
    })
    expect(wrapper.classes()).toContain('bg-yellow-100')
    expect(wrapper.classes()).toContain('text-yellow-800')
  })

  it('应该应用正确的样式类 - info', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        status: '信息',
        type: 'info'
      }
    })
    expect(wrapper.classes()).toContain('bg-blue-100')
    expect(wrapper.classes()).toContain('text-blue-800')
  })

  it('默认类型应该是 default', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        status: '默认'
      }
    })
    expect(wrapper.classes()).toContain('bg-gray-100')
    expect(wrapper.classes()).toContain('text-gray-800')
  })
})
