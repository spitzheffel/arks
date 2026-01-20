import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmDialog from './ConfirmDialog.vue'

describe('ConfirmDialog', () => {
  it('renders correctly when visible', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    expect(wrapper.text()).toContain('确定要删除吗？')
    expect(wrapper.text()).toContain('确认操作')
  })

  it('does not render when not visible', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: false,
        message: '确定要删除吗？'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    expect(wrapper.text()).not.toContain('确定要删除吗？')
  })

  it('displays custom title', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        title: '删除数据源',
        message: '确定要删除吗？'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    expect(wrapper.text()).toContain('删除数据源')
  })

  it('displays danger type with trash icon', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？',
        type: 'danger'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    expect(wrapper.text()).toContain('🗑️')
  })

  it('emits confirm event when confirm button clicked', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    const confirmButton = wrapper.findAll('button').find(btn => btn.text().includes('确认'))
    await confirmButton?.trigger('click')
    
    expect(wrapper.emitted('confirm')).toBeTruthy()
  })

  it('emits cancel event when cancel button clicked', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    const cancelButton = wrapper.findAll('button').find(btn => btn.text().includes('取消'))
    await cancelButton?.trigger('click')
    
    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('update:visible')).toBeTruthy()
  })

  it('shows loading state on confirm button', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？',
        loading: true
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    expect(wrapper.text()).toContain('处理中...')
  })

  it('disables buttons when loading', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？',
        loading: true
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    const buttons = wrapper.findAll('button')
    buttons.forEach(btn => {
      expect(btn.attributes('disabled')).toBeDefined()
    })
  })

  it('uses danger button style for danger type', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？',
        type: 'danger'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    const confirmButton = wrapper.findAll('button').find(btn => btn.text().includes('确认'))
    expect(confirmButton?.classes()).toContain('btn-danger')
  })

  it('displays custom confirm and cancel text', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {
        visible: true,
        message: '确定要删除吗？',
        confirmText: '删除',
        cancelText: '返回'
      },
      global: {
        stubs: {
          Teleport: true
        }
      }
    })
    
    expect(wrapper.text()).toContain('删除')
    expect(wrapper.text()).toContain('返回')
  })
})
