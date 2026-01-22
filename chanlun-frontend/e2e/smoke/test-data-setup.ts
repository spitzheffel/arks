import { Page, expect } from '@playwright/test'

/**
 * E2E 测试数据准备工具
 * 
 * 提供创建测试所需数据的辅助函数
 */

// 测试数据常量
export const TEST_DATASOURCE_NAME = `E2E测试数据源_${Date.now()}`

// 辅助函数：等待页面标题
export async function waitForPageTitle(page: Page, title: string) {
  await expect(page.getByRole('heading', { name: title })).toBeVisible({ timeout: 15000 })
}

// 辅助函数：等待加载完成
export async function waitForLoading(page: Page) {
  await page.waitForTimeout(500)
  const spinner = page.locator('.animate-spin')
  if (await spinner.isVisible()) {
    await expect(spinner).not.toBeVisible({ timeout: 20000 })
  }
}

// 辅助函数：格式化日期为 datetime-local 格式
export function formatDateTimeLocal(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

/**
 * 创建测试数据源
 */
export async function createTestDataSource(page: Page, name: string): Promise<boolean> {
  await page.goto('/datasources')
  await waitForPageTitle(page, '数据源管理')
  await waitForLoading(page)

  // 检查是否已存在
  const existingRow = page.locator('tr').filter({ hasText: name })
  if (await existingRow.count() > 0) {
    return true
  }

  // 创建新数据源
  await page.locator('button').filter({ hasText: '新增数据源' }).first().click()
  const formDialog = page.locator('.fixed.inset-0.z-50')
  await expect(formDialog).toBeVisible({ timeout: 5000 })
  await formDialog.locator('input[type="text"]').first().fill(name)
  await formDialog.locator('button').filter({ hasText: '创建数据源' }).click()
  await expect(formDialog).not.toBeVisible({ timeout: 10000 })
  await waitForLoading(page)

  // 验证创建成功
  const newRow = page.locator('tr').filter({ hasText: name })
  return await newRow.count() > 0
}

/**
 * 同步市场数据
 */
export async function syncMarkets(page: Page): Promise<boolean> {
  await page.goto('/markets')
  await waitForPageTitle(page, '市场管理')
  await waitForLoading(page)

  const syncBtn = page.locator('button').filter({ hasText: '同步市场' })
  if (await syncBtn.count() > 0 && await syncBtn.first().isEnabled()) {
    await syncBtn.first().click()
    await waitForLoading(page)
    await page.waitForTimeout(2000)
    
    // 关闭可能出现的结果弹窗
    const resultDialog = page.locator('.fixed.inset-0.z-50')
    if (await resultDialog.isVisible()) {
      const closeBtn = resultDialog.locator('button').filter({ hasText: /确定|关闭/ })
      if (await closeBtn.count() > 0) {
        await closeBtn.click()
        await page.waitForTimeout(500)
      }
    }
    return true
  }
  return false
}

/**
 * 同步交易对数据
 */
export async function syncSymbols(page: Page): Promise<boolean> {
  await page.goto('/symbols')
  await waitForPageTitle(page, '交易对管理')
  await waitForLoading(page)

  // 选择第一个市场
  const marketSelect = page.locator('select').nth(1)
  if (await marketSelect.count() > 0) {
    const options = await marketSelect.locator('option').all()
    for (let i = 1; i < options.length; i++) {
      const optionValue = await options[i].getAttribute('value')
      if (optionValue && optionValue !== '') {
        await marketSelect.selectOption({ index: i })
        await waitForLoading(page)
        break
      }
    }
  }

  const syncBtn = page.locator('button').filter({ hasText: '同步交易对' })
  if (await syncBtn.count() > 0 && await syncBtn.first().isEnabled()) {
    await syncBtn.first().click()
    await waitForLoading(page)
    await page.waitForTimeout(2000)
    
    // 关闭可能出现的结果弹窗
    const resultDialog = page.locator('.fixed.inset-0.z-50')
    if (await resultDialog.isVisible()) {
      const closeBtn = resultDialog.locator('button').filter({ hasText: /确定|关闭/ })
      if (await closeBtn.count() > 0) {
        await closeBtn.click()
        await page.waitForTimeout(500)
      }
    }
    return true
  }
  return false
}

/**
 * 开启交易对的历史同步
 */
export async function enableHistorySyncForFirstSymbol(page: Page): Promise<boolean> {
  await page.goto('/symbols')
  await waitForPageTitle(page, '交易对管理')
  await waitForLoading(page)

  const symbolRows = page.locator('tbody tr')
  if (await symbolRows.count() === 0) {
    return false
  }

  // 找到历史同步列的开关 (第5列)
  const historySyncToggle = symbolRows.first().locator('td:nth-child(5) button[role="switch"]')
  if (await historySyncToggle.count() === 0) {
    return false
  }

  const isEnabled = await historySyncToggle.getAttribute('aria-checked')
  if (isEnabled === 'true') {
    return true // 已经开启
  }

  await historySyncToggle.click()
  await page.waitForTimeout(500)
  
  // 确认弹窗
  const confirmDialog = page.locator('.fixed.inset-0.z-50')
  if (await confirmDialog.isVisible()) {
    const confirmBtn = confirmDialog.locator('button').filter({ hasText: /确认开启|确认/ })
    if (await confirmBtn.count() > 0) {
      await confirmBtn.click()
      await expect(confirmDialog).not.toBeVisible({ timeout: 5000 })
    }
  }
  await waitForLoading(page)
  return true
}

/**
 * 执行缺口检测（批量）
 */
export async function detectGaps(page: Page): Promise<boolean> {
  await page.goto('/gaps')
  await waitForPageTitle(page, '缺口管理')
  await waitForLoading(page)

  await page.locator('button').filter({ hasText: '检测缺口' }).click()
  const dialog = page.locator('.fixed.inset-0.z-50').first()
  await expect(dialog).toBeVisible()

  // 勾选批量检测
  const batchCheckbox = dialog.locator('#detectAll')
  await batchCheckbox.check()

  // 点击开始检测
  const detectBtn = dialog.locator('button').filter({ hasText: '开始检测' })
  await detectBtn.click()

  // 等待检测完成
  await page.waitForTimeout(1000)
  const resultHeading = page.getByRole('heading', { name: /检测完成|检测失败/ })
  await expect(resultHeading).toBeVisible({ timeout: 30000 })

  // 关闭结果弹窗
  const closeBtn = page.locator('.fixed.inset-0.z-50').first().locator('button').filter({ hasText: '确定' })
  await closeBtn.click()
  await page.waitForTimeout(500)
  
  return true
}

/**
 * 删除测试数据源
 */
export async function deleteTestDataSource(page: Page, name: string): Promise<void> {
  await page.goto('/datasources')
  await waitForPageTitle(page, '数据源管理')
  await waitForLoading(page)

  const testRow = page.locator('tr').filter({ hasText: name })
  if (await testRow.count() > 0) {
    const deleteBtn = testRow.locator('button').filter({ hasText: '删除' })
    if (await deleteBtn.count() > 0) {
      await deleteBtn.click()
      await page.waitForTimeout(500)
      const confirmDialog = page.locator('.fixed.inset-0.z-50')
      if (await confirmDialog.isVisible()) {
        const confirmBtn = confirmDialog.locator('button').filter({ hasText: '确认' })
        if (await confirmBtn.count() > 0) {
          await confirmBtn.click()
          await waitForLoading(page)
        }
      }
    }
  }
}

/**
 * 完整的测试数据准备流程
 * 创建数据源 -> 同步市场 -> 同步交易对 -> 开启历史同步
 */
export async function setupFullTestData(page: Page, dataSourceName: string): Promise<boolean> {
  try {
    // 1. 创建数据源
    const dsCreated = await createTestDataSource(page, dataSourceName)
    if (!dsCreated) {
      console.log('Failed to create data source')
      return false
    }

    // 2. 同步市场
    await syncMarkets(page)

    // 3. 同步交易对
    await syncSymbols(page)

    // 4. 开启历史同步
    const syncEnabled = await enableHistorySyncForFirstSymbol(page)
    if (!syncEnabled) {
      console.log('Failed to enable history sync')
      return false
    }

    return true
  } catch (e) {
    console.error('Setup test data failed:', e)
    return false
  }
}
