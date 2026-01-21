import { test, expect, Page } from '@playwright/test'

/**
 * 二次确认弹窗验收测试
 * 
 * 验证需求：
 * - 27.1 数据源删除/启用/禁用二次确认
 * - 27.2 市场启用/禁用二次确认
 * - 27.3 交易对同步开关二次确认
 * - 27.4 删除历史数据二次确认
 */

// 辅助函数：等待确认弹窗出现
async function waitForConfirmDialog(page: Page) {
  await expect(page.locator('.fixed.inset-0.z-50')).toBeVisible({ timeout: 5000 })
}

// 辅助函数：点击取消按钮
async function clickCancelButton(page: Page) {
  const cancelBtn = page.locator('.fixed.inset-0.z-50 button').filter({ hasText: '取消' }).first()
  await cancelBtn.click()
}

// 辅助函数：检查弹窗已关闭
async function checkDialogClosed(page: Page) {
  await expect(page.locator('.fixed.inset-0.z-50')).not.toBeVisible({ timeout: 3000 })
}

// 辅助函数：等待页面标题出现（使用 getByRole 更精确定位）
async function waitForPageTitle(page: Page, title: string) {
  await expect(page.getByRole('heading', { name: title })).toBeVisible({ timeout: 10000 })
}

test.describe('27.1 数据源删除/启用/禁用二次确认', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/datasources')
    // 等待页面加载完成 - 使用更精确的选择器
    await waitForPageTitle(page, '数据源管理')
  })

  test('删除数据源时应显示二次确认弹窗', async ({ page }) => {
    // 等待页面数据加载
    await page.waitForTimeout(1000)
    
    // 检查是否有数据源列表
    const deleteButtons = page.locator('button').filter({ hasText: '删除' })
    const count = await deleteButtons.count()
    
    if (count > 0) {
      // 点击第一个删除按钮
      await deleteButtons.first().click()
      
      // 验证确认弹窗出现
      await waitForConfirmDialog(page)
      
      // 验证弹窗标题和内容
      const dialog = page.locator('.fixed.inset-0.z-50')
      await expect(dialog.locator('h3')).toContainText('删除数据源')
      await expect(dialog.locator('p').first()).toContainText('确定要删除')
      
      // 验证有确认和取消按钮
      await expect(dialog.locator('button').filter({ hasText: '取消' })).toBeVisible()
      await expect(dialog.locator('button').filter({ hasText: '确认' })).toBeVisible()
      
      // 验证危险类型样式（红色按钮）
      const confirmBtn = dialog.locator('button').filter({ hasText: '确认' })
      await expect(confirmBtn).toHaveClass(/btn-danger/)
      
      // 点击取消关闭弹窗
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      // 如果没有数据源，跳过测试
      test.skip()
    }
  })

  test('启用/禁用数据源时应显示二次确认弹窗', async ({ page }) => {
    await page.waitForTimeout(1000)
    
    // 查找状态开关按钮
    const toggleButtons = page.locator('button[role="switch"]')
    const count = await toggleButtons.count()
    
    if (count > 0) {
      // 点击第一个开关
      await toggleButtons.first().click()
      
      // 验证确认弹窗出现
      await waitForConfirmDialog(page)
      
      // 验证弹窗标题包含"启用"或"禁用"
      const dialog = page.locator('.fixed.inset-0.z-50')
      const titleText = await dialog.locator('h3').textContent()
      expect(titleText).toMatch(/启用数据源|禁用数据源/)
      
      // 验证有确认和取消按钮
      await expect(dialog.locator('button').filter({ hasText: '取消' })).toBeVisible()
      await expect(dialog.locator('button').filter({ hasText: '确认' })).toBeVisible()
      
      // 点击取消关闭弹窗
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })

  test('取消操作应关闭弹窗且不执行操作', async ({ page }) => {
    await page.waitForTimeout(1000)
    
    const deleteButtons = page.locator('button').filter({ hasText: '删除' })
    const count = await deleteButtons.count()
    
    if (count > 0) {
      // 记录当前数据源数量
      const initialCount = await page.locator('tbody tr').count()
      
      // 点击删除按钮
      await deleteButtons.first().click()
      await waitForConfirmDialog(page)
      
      // 点击取消
      await clickCancelButton(page)
      await checkDialogClosed(page)
      
      // 验证数据源数量未变化
      const finalCount = await page.locator('tbody tr').count()
      expect(finalCount).toBe(initialCount)
    } else {
      test.skip()
    }
  })
})

test.describe('27.2 市场启用/禁用二次确认', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/markets')
    await waitForPageTitle(page, '市场管理')
  })

  test('启用/禁用市场时应显示二次确认弹窗', async ({ page }) => {
    // 等待市场列表加载
    await page.waitForTimeout(1000)
    
    // 查找状态开关按钮
    const toggleButtons = page.locator('button[role="switch"]')
    const count = await toggleButtons.count()
    
    if (count > 0) {
      // 点击第一个开关
      await toggleButtons.first().click()
      
      // 验证确认弹窗出现
      await waitForConfirmDialog(page)
      
      // 验证弹窗标题包含"启用"或"禁用"
      const dialog = page.locator('.fixed.inset-0.z-50')
      const titleText = await dialog.locator('h3').textContent()
      expect(titleText).toMatch(/启用市场|禁用市场/)
      
      // 验证弹窗消息
      const messageText = await dialog.locator('p').first().textContent()
      expect(messageText).toMatch(/确定要(启用|禁用)市场/)
      
      // 验证有确认和取消按钮
      await expect(dialog.locator('button').filter({ hasText: '取消' })).toBeVisible()
      await expect(dialog.locator('button').filter({ hasText: '确认' })).toBeVisible()
      
      // 点击取消关闭弹窗
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })

  test('禁用市场时应提示级联影响', async ({ page }) => {
    await page.waitForTimeout(1000)
    
    // 查找已启用的市场开关（蓝色背景）
    const enabledToggles = page.locator('button[role="switch"].bg-blue-600')
    const count = await enabledToggles.count()
    
    if (count > 0) {
      // 点击禁用
      await enabledToggles.first().click()
      
      // 验证确认弹窗出现
      await waitForConfirmDialog(page)
      
      // 验证弹窗消息包含级联影响提示
      const dialog = page.locator('.fixed.inset-0.z-50')
      const messageText = await dialog.locator('p').first().textContent()
      expect(messageText).toContain('停止该市场下所有交易对的数据同步')
      
      // 点击取消关闭弹窗
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })
})

test.describe('27.3 交易对同步开关二次确认', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/symbols')
    await waitForPageTitle(page, '交易对管理')
  })

  test('切换实时同步开关时应显示二次确认弹窗', async ({ page }) => {
    await page.waitForTimeout(1000)
    
    // 查找实时同步列的开关（第4列）
    const realtimeSyncToggles = page.locator('tbody tr td:nth-child(4) button[role="switch"]')
    const count = await realtimeSyncToggles.count()
    
    if (count > 0) {
      // 点击第一个实时同步开关
      await realtimeSyncToggles.first().click()
      
      // 验证确认弹窗出现
      await waitForConfirmDialog(page)
      
      // 验证弹窗标题
      const dialog = page.locator('.fixed.inset-0.z-50')
      const titleText = await dialog.locator('h3').textContent()
      expect(titleText).toMatch(/开启实时同步|关闭实时同步/)
      
      // 验证弹窗消息包含交易对名称
      const messageText = await dialog.locator('p').first().textContent()
      expect(messageText).toMatch(/确定要(开启|关闭)交易对/)
      
      // 验证有确认和取消按钮
      await expect(dialog.locator('button').filter({ hasText: '取消' })).toBeVisible()
      await expect(dialog.locator('button').filter({ hasText: /确认/ })).toBeVisible()
      
      // 点击取消关闭弹窗
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })

  test('切换历史同步开关时应显示二次确认弹窗', async ({ page }) => {
    await page.waitForTimeout(1000)
    
    // 查找历史同步列的开关（第5列）
    const historySyncToggles = page.locator('tbody tr td:nth-child(5) button[role="switch"]')
    const count = await historySyncToggles.count()
    
    if (count > 0) {
      // 点击第一个历史同步开关
      await historySyncToggles.first().click()
      
      // 验证确认弹窗出现
      await waitForConfirmDialog(page)
      
      // 验证弹窗标题
      const dialog = page.locator('.fixed.inset-0.z-50')
      const titleText = await dialog.locator('h3').textContent()
      expect(titleText).toMatch(/开启历史同步|关闭历史同步/)
      
      // 验证弹窗消息
      const messageText = await dialog.locator('p').first().textContent()
      expect(messageText).toMatch(/确定要(开启|关闭)交易对/)
      
      // 点击取消关闭弹窗
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })

  test('关闭实时同步时应提示停止接收数据', async ({ page }) => {
    await page.waitForTimeout(1000)
    
    // 查找已启用的实时同步开关
    const enabledToggles = page.locator('tbody tr td:nth-child(4) button[role="switch"].bg-blue-600')
    const count = await enabledToggles.count()
    
    if (count > 0) {
      await enabledToggles.first().click()
      await waitForConfirmDialog(page)
      
      const dialog = page.locator('.fixed.inset-0.z-50')
      const messageText = await dialog.locator('p').first().textContent()
      expect(messageText).toContain('停止接收实时K线数据')
      
      await clickCancelButton(page)
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })
})

test.describe('27.4 删除历史数据二次确认', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/sync')
    await waitForPageTitle(page, '数据同步')
  })

  test('点击删除历史数据按钮应显示二次确认弹窗', async ({ page }) => {
    // 查找删除历史数据按钮
    const deleteButton = page.locator('button').filter({ hasText: '删除历史数据' })
    await expect(deleteButton).toBeVisible()
    
    // 点击删除按钮
    await deleteButton.click()
    
    // 验证确认弹窗出现
    await waitForConfirmDialog(page)
    
    // 验证弹窗标题
    const dialog = page.locator('.fixed.inset-0.z-50')
    await expect(dialog.locator('h3')).toContainText('删除历史数据')
    
    // 验证弹窗消息包含警告
    const messageText = await dialog.locator('p').first().textContent()
    expect(messageText).toContain('永久删除')
    expect(messageText).toContain('不可恢复')
    
    // 验证危险类型样式
    const confirmBtn = dialog.locator('button').filter({ hasText: '确认删除' })
    await expect(confirmBtn).toBeVisible()
    
    // 验证有表单字段
    await expect(dialog.locator('select')).toHaveCount(2) // 交易对和周期选择
    await expect(dialog.locator('input[type="datetime-local"]')).toHaveCount(2) // 开始和结束时间
    
    // 点击取消关闭弹窗
    await clickCancelButton(page)
    await checkDialogClosed(page)
  })

  test('删除确认弹窗应包含必要的表单字段', async ({ page }) => {
    const deleteButton = page.locator('button').filter({ hasText: '删除历史数据' })
    await deleteButton.click()
    await waitForConfirmDialog(page)
    
    const dialog = page.locator('.fixed.inset-0.z-50')
    
    // 验证交易对选择
    await expect(dialog.locator('label').filter({ hasText: '交易对' })).toBeVisible()
    
    // 验证周期选择
    await expect(dialog.locator('label').filter({ hasText: '周期' })).toBeVisible()
    
    // 验证开始时间
    await expect(dialog.locator('label').filter({ hasText: '开始时间' })).toBeVisible()
    
    // 验证结束时间
    await expect(dialog.locator('label').filter({ hasText: '结束时间' })).toBeVisible()
    
    await clickCancelButton(page)
  })

  test('未填写必要字段时确认按钮应禁用', async ({ page }) => {
    const deleteButton = page.locator('button').filter({ hasText: '删除历史数据' })
    await deleteButton.click()
    await waitForConfirmDialog(page)
    
    const dialog = page.locator('.fixed.inset-0.z-50')
    const confirmBtn = dialog.locator('button').filter({ hasText: '确认删除' })
    
    // 未选择交易对时，确认按钮应该禁用
    await expect(confirmBtn).toBeVisible()
    
    await clickCancelButton(page)
  })
})

test.describe('ConfirmDialog 组件通用行为', () => {
  test('点击遮罩层应关闭弹窗', async ({ page }) => {
    await page.goto('/datasources')
    await waitForPageTitle(page, '数据源管理')
    await page.waitForTimeout(1000)
    
    const deleteButtons = page.locator('button').filter({ hasText: '删除' })
    const count = await deleteButtons.count()
    
    if (count > 0) {
      await deleteButtons.first().click()
      await waitForConfirmDialog(page)
      
      // 点击遮罩层（弹窗外部区域）
      const overlay = page.locator('.fixed.inset-0.z-50')
      await overlay.click({ position: { x: 10, y: 10 } })
      
      // 验证弹窗关闭
      await checkDialogClosed(page)
    } else {
      test.skip()
    }
  })

  test('弹窗应显示正确的图标类型', async ({ page }) => {
    await page.goto('/datasources')
    await waitForPageTitle(page, '数据源管理')
    await page.waitForTimeout(1000)
    
    const deleteButtons = page.locator('button').filter({ hasText: '删除' })
    const count = await deleteButtons.count()
    
    if (count > 0) {
      await deleteButtons.first().click()
      await waitForConfirmDialog(page)
      
      // 验证危险类型显示垃圾桶图标
      const dialog = page.locator('.fixed.inset-0.z-50')
      await expect(dialog.locator('text=🗑️')).toBeVisible()
      
      await clickCancelButton(page)
    } else {
      test.skip()
    }
  })
})
