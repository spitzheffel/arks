import { test, expect, Page } from '@playwright/test'

/**
 * E2E 冒烟测试 - 数据源新增 + 连接测试
 * 
 * 验证任务 31.2:
 * - 新增数据源
 * - 测试数据源连接
 * 
 * 前置条件:
 * - 后端启动时设置 EXCHANGE_API_MOCK=true
 * - 数据库已初始化
 */

// 测试数据 - 使用时间戳确保唯一性
const getTestDataSourceName = () => `E2E测试数据源_${Date.now()}`

const TEST_DATASOURCE = {
  exchangeType: 'BINANCE',
  baseUrl: 'https://api.binance.com',
  wsUrl: 'wss://stream.binance.com:9443/ws'
}

// 辅助函数：等待页面标题
async function waitForPageTitle(page: Page, title: string) {
  await expect(page.getByRole('heading', { name: title })).toBeVisible({ timeout: 10000 })
}

// 辅助函数：等待加载完成
async function waitForLoading(page: Page) {
  // 等待加载指示器消失
  await page.waitForTimeout(500)
  const spinner = page.locator('.animate-spin')
  if (await spinner.isVisible()) {
    await expect(spinner).not.toBeVisible({ timeout: 10000 })
  }
}

// 辅助函数：清理测试数据源
async function cleanupTestDataSource(page: Page, name: string) {
  await page.goto('/datasources')
  await waitForPageTitle(page, '数据源管理')
  await waitForLoading(page)
  
  // 查找测试数据源的删除按钮
  const testRow = page.locator('tr').filter({ hasText: name })
  if (await testRow.count() > 0) {
    const deleteBtn = testRow.locator('button').filter({ hasText: '删除' })
    if (await deleteBtn.count() > 0) {
      await deleteBtn.click()
      // 确认删除
      const confirmBtn = page.locator('.fixed.inset-0.z-50 button').filter({ hasText: '确认' })
      if (await confirmBtn.isVisible()) {
        await confirmBtn.click()
        await waitForLoading(page)
      }
    }
  }
}

test.describe('31.2 数据源新增 + 连接测试', () => {
  test('应该能够新增数据源', async ({ page }) => {
    const testName = getTestDataSourceName()
    
    // 1. 导航到数据源管理页面
    await page.goto('/datasources')
    await waitForPageTitle(page, '数据源管理')

    // 2. 点击新增按钮 - 使用 first() 避免多个按钮的问题
    const addButton = page.locator('button').filter({ hasText: '新增数据源' }).first()
    await expect(addButton).toBeVisible()
    await addButton.click()

    // 3. 等待表单弹窗出现
    const formDialog = page.locator('.fixed.inset-0.z-50')
    await expect(formDialog).toBeVisible()
    await expect(formDialog.locator('h3')).toContainText('新增数据源')

    // 4. 填写表单
    // 数据源名称
    const nameInput = formDialog.locator('input[type="text"]').first()
    await nameInput.fill(testName)

    // 交易所类型 (默认已选择 BINANCE)
    const exchangeSelect = formDialog.locator('select').first()
    await expect(exchangeSelect).toHaveValue('BINANCE')

    // 5. 提交表单
    const submitButton = formDialog.locator('button').filter({ hasText: '创建数据源' })
    await submitButton.click()

    // 6. 等待弹窗关闭
    await expect(formDialog).not.toBeVisible({ timeout: 10000 })

    // 7. 验证数据源已创建
    await waitForLoading(page)
    const newRow = page.locator('tr').filter({ hasText: testName })
    await expect(newRow).toBeVisible()
    
    // 验证交易所类型显示正确
    await expect(newRow.locator('text=币安')).toBeVisible()
    
    // 清理
    await cleanupTestDataSource(page, testName)
  })

  test('应该能够测试数据源连接', async ({ page }) => {
    const testName = getTestDataSourceName()
    
    // 1. 先创建一个数据源
    await page.goto('/datasources')
    await waitForPageTitle(page, '数据源管理')

    // 点击新增 - 使用 first() 避免多个按钮的问题
    await page.locator('button').filter({ hasText: '新增数据源' }).first().click()
    const formDialog = page.locator('.fixed.inset-0.z-50')
    await expect(formDialog).toBeVisible()

    // 填写名称
    await formDialog.locator('input[type="text"]').first().fill(testName)
    
    // 提交
    await formDialog.locator('button').filter({ hasText: '创建数据源' }).click()
    await expect(formDialog).not.toBeVisible({ timeout: 10000 })
    await waitForLoading(page)

    // 2. 找到新创建的数据源行
    const testRow = page.locator('tr').filter({ hasText: testName })
    await expect(testRow).toBeVisible()

    // 3. 点击测试连接按钮
    const testButton = testRow.locator('button').filter({ hasText: '测试' })
    await testButton.click()

    // 4. 等待连接测试弹窗出现
    const testDialog = page.locator('.fixed.inset-0.z-50')
    await expect(testDialog).toBeVisible()
    await expect(testDialog.locator('h3')).toContainText('连接测试')

    // 5. 等待测试完成 (Mock 模式下应该很快)
    // 等待加载指示器消失
    const loadingSpinner = testDialog.locator('.animate-spin')
    if (await loadingSpinner.isVisible()) {
      await expect(loadingSpinner).not.toBeVisible({ timeout: 10000 })
    }

    // 6. 验证连接成功 (Mock 模式下应该总是成功)
    await expect(testDialog.locator('text=连接成功')).toBeVisible({ timeout: 5000 })
    
    // 验证显示响应延迟
    await expect(testDialog.locator('text=响应延迟')).toBeVisible()

    // 7. 关闭弹窗
    const closeButton = testDialog.locator('button').filter({ hasText: '关闭' })
    await closeButton.click()
    await expect(testDialog).not.toBeVisible()
    
    // 清理
    await cleanupTestDataSource(page, testName)
  })

  test('新增数据源后应该默认启用状态', async ({ page }) => {
    const testName = getTestDataSourceName()
    
    // 1. 创建数据源
    await page.goto('/datasources')
    await waitForPageTitle(page, '数据源管理')

    await page.locator('button').filter({ hasText: '新增数据源' }).first().click()
    const formDialog = page.locator('.fixed.inset-0.z-50')
    await formDialog.locator('input[type="text"]').first().fill(testName)
    await formDialog.locator('button').filter({ hasText: '创建数据源' }).click()
    await expect(formDialog).not.toBeVisible({ timeout: 10000 })
    await waitForLoading(page)

    // 2. 验证数据源状态开关
    const testRow = page.locator('tr').filter({ hasText: testName })
    const statusToggle = testRow.locator('button[role="switch"]')
    
    // 新创建的数据源默认应该是启用状态 (enabled=true)
    await expect(statusToggle).toHaveAttribute('aria-checked', 'true')
    
    // 清理
    await cleanupTestDataSource(page, testName)
  })

  test('应该能够启用/禁用数据源', async ({ page }) => {
    const testName = getTestDataSourceName()
    
    // 1. 创建数据源
    await page.goto('/datasources')
    await waitForPageTitle(page, '数据源管理')

    await page.locator('button').filter({ hasText: '新增数据源' }).first().click()
    const formDialog = page.locator('.fixed.inset-0.z-50')
    await formDialog.locator('input[type="text"]').first().fill(testName)
    await formDialog.locator('button').filter({ hasText: '创建数据源' }).click()
    await expect(formDialog).not.toBeVisible({ timeout: 10000 })
    await waitForLoading(page)

    // 2. 点击状态开关
    const testRow = page.locator('tr').filter({ hasText: testName })
    const statusToggle = testRow.locator('button[role="switch"]')
    await statusToggle.click()

    // 3. 验证确认弹窗出现
    const confirmDialog = page.locator('.fixed.inset-0.z-50')
    await expect(confirmDialog).toBeVisible()
    await expect(confirmDialog.locator('h3')).toContainText(/启用数据源|禁用数据源/)

    // 4. 确认操作
    const confirmBtn = confirmDialog.locator('button').filter({ hasText: '确认' })
    await confirmBtn.click()

    // 5. 等待操作完成
    await expect(confirmDialog).not.toBeVisible({ timeout: 5000 })
    await waitForLoading(page)
    
    // 清理
    await cleanupTestDataSource(page, testName)
  })
})
