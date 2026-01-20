import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from './EmptyState.vue'

describe('EmptyState', () => {
  it('应该渲染标题', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: '暂无数据'
      }
    })
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('应该渲染描述文本', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: '暂无数据',
        description: '请添加一些数据'
      }
    })
    expect(wrapper.text()).toContain('请添加一些数据')
  })

  it('应该使用默认图标', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: '暂无数据'
      }
    })
    expect(wrapper.text()).toContain('📭')
  })

  it('应该使用自定义图标', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: '暂无数据',
        icon: '🔍'
      }
    })
    expect(wrapper.text()).toContain('🔍')
  })

  it('应该渲染 action 插槽', () => {
    const wrapper = mount(EmptyState, {
      props: {
        title: '暂无数据'
      },
      slots: {
        action: '<button>添加</button>'
      }
    })
    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.find('button').text()).toBe('添加')
  })
})
