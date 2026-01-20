import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoadingSpinner from './LoadingSpinner.vue'

describe('LoadingSpinner', () => {
  it('应该渲染加载动画', () => {
    const wrapper = mount(LoadingSpinner)
    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.find('svg').classes()).toContain('animate-spin')
  })

  it('应该显示加载文本', () => {
    const wrapper = mount(LoadingSpinner, {
      props: {
        text: '加载中...'
      }
    })
    expect(wrapper.text()).toContain('加载中...')
  })

  it('不传 text 时不应该显示文本', () => {
    const wrapper = mount(LoadingSpinner)
    expect(wrapper.find('span').exists()).toBe(false)
  })

  it('应该应用正确的尺寸类 - sm', () => {
    const wrapper = mount(LoadingSpinner, {
      props: {
        size: 'sm'
      }
    })
    expect(wrapper.find('svg').classes()).toContain('h-4')
    expect(wrapper.find('svg').classes()).toContain('w-4')
  })

  it('应该应用正确的尺寸类 - md (默认)', () => {
    const wrapper = mount(LoadingSpinner)
    expect(wrapper.find('svg').classes()).toContain('h-8')
    expect(wrapper.find('svg').classes()).toContain('w-8')
  })

  it('应该应用正确的尺寸类 - lg', () => {
    const wrapper = mount(LoadingSpinner, {
      props: {
        size: 'lg'
      }
    })
    expect(wrapper.find('svg').classes()).toContain('h-12')
    expect(wrapper.find('svg').classes()).toContain('w-12')
  })

  it('fullscreen 模式应该应用全屏样式', () => {
    const wrapper = mount(LoadingSpinner, {
      props: {
        fullscreen: true
      }
    })
    expect(wrapper.classes()).toContain('fixed')
    expect(wrapper.classes()).toContain('inset-0')
    expect(wrapper.classes()).toContain('z-50')
  })
})
