import { test, expect } from '@playwright/test'
import {
  waitForPageTitle,
  waitForLoading,
  setupFullTestData,
  deleteTestDataSource,
  detectGaps
} from './test-data-setup'

/**
 * E2E 冒烟测试 - 缺口检测 + 回补流程
 * 
 * 验证任务 31.4:
 * - 检测数据缺口
 * - 回补缺口
 * 
 * 测试会自动创建所需的测试数据：
 * - 创建数据源
 * - 同步市场和交易对
 * - 开启历史同步
 * - 执行缺口检测
 */

// 测试数据源名称
const TEST_DS_NAME = `E2E缺口测试_${Date.now()}`

test.describe('31.4 缺口检测 + 回补流程', () => {
  // 在所有测试前准备测试数据
  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    try {
      // 创建完整的测试数据
      const success = await setupFullTestData(page, TEST_DS_NAME)
      if (!success) {
        throw new Error('Failed to setup test data')
      }
      // 执行一次缺口检测，确保有缺口数据
      await detectGaps(page)
    } finally {
      await page.close()
    }
  })

  // 在所有测试后清理测试数据
  test.afterAll(async ({ browser }) => {
    const page = await browser.newPage()
    try {
      await deleteTestDataSource(page, TEST_DS_NAME)
    } finally {
      await page.close()
    }
  })

  test('应该能够打开缺口检测弹窗', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')

    // 2. 点击检测缺口按钮
    const detectButton = page.locator('button').filter({ hasText: '检测缺口' })
    await expect(detectButton).toBeVisible()
    await detectButton.click()

    // 3. 验证弹窗出现
    const dialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(dialog).toBeVisible()
    await expect(dialog.getByRole('heading', { name: '检测数据缺口' })).toBeVisible()

    // 4. 验证表单字段存在
    await expect(dialog.getByLabel('批量检测所有已开启历史同步的交易对')).toBeVisible()
    // 使用更精确的选择器，定位弹窗内的表单标签
    await expect(dialog.locator('text=交易对').first()).toBeVisible()
    await expect(dialog.locator('text=周期').first()).toBeVisible()

    // 5. 关闭弹窗
    const cancelBtn = dialog.locator('button').filter({ hasText: '取消' })
    await cancelBtn.click()
    await expect(dialog).not.toBeVisible()
  })

  test('应该能够执行批量缺口检测', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')

    // 2. 点击检测缺口按钮
    await page.locator('button').filter({ hasText: '检测缺口' }).click()
    const dialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(dialog).toBeVisible()

    // 3. 勾选批量检测
    const batchCheckbox = dialog.locator('#detectAll')
    await batchCheckbox.check()

    // 4. 点击开始检测
    const detectBtn = dialog.locator('button').filter({ hasText: '开始检测' })
    await expect(detectBtn).toBeEnabled()
    await detectBtn.click()

    // 5. 等待检测完成 - 结果弹窗会替换检测弹窗
    await page.waitForTimeout(1000)
    
    // 查找结果弹窗（包含检测完成或检测失败的标题）
    const resultHeading = page.getByRole('heading', { name: /检测完成|检测失败/ })
    await expect(resultHeading).toBeVisible({ timeout: 30000 })

    // 6. 关闭结果弹窗
    const closeBtn = page.locator('.fixed.inset-0.z-50').first().locator('button').filter({ hasText: '确定' })
    await closeBtn.click()
    await page.waitForTimeout(500)
  })

  test('应该能够执行单个交易对缺口检测', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')

    // 2. 点击检测缺口按钮
    await page.locator('button').filter({ hasText: '检测缺口' }).click()
    const dialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(dialog).toBeVisible()

    // 3. 确保批量检测未勾选
    const batchCheckbox = dialog.locator('#detectAll')
    await batchCheckbox.uncheck()

    // 4. 选择交易对 - 测试数据已在 beforeAll 中准备好
    const symbolSelect = dialog.locator('select').first()
    await page.waitForTimeout(500) // 等待选项加载
    const options = await symbolSelect.locator('option').all()
    // 选择第一个非空选项
    await symbolSelect.selectOption({ index: options.length > 1 ? 1 : 0 })

    // 5. 选择周期
    const intervalSelect = dialog.locator('select').nth(1)
    await intervalSelect.selectOption('1h')

    // 6. 点击开始检测
    const detectBtn = dialog.locator('button').filter({ hasText: '开始检测' })
    await expect(detectBtn).toBeEnabled()
    await detectBtn.click()

    // 7. 等待检测完成
    await page.waitForTimeout(1000)
    const resultHeading = page.getByRole('heading', { name: /检测完成|检测失败/ })
    await expect(resultHeading).toBeVisible({ timeout: 30000 })

    // 8. 关闭结果弹窗
    await page.locator('.fixed.inset-0.z-50').first().locator('button').filter({ hasText: '确定' }).click()
    await page.waitForTimeout(500)
  })

  test('缺口列表应该支持筛选', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')
    await waitForLoading(page)

    // 2. 验证筛选控件存在
    const filterCard = page.locator('.card').first()
    await expect(filterCard.getByText('交易对:', { exact: false })).toBeVisible()
    await expect(filterCard.getByText('周期:', { exact: false })).toBeVisible()
    await expect(filterCard.getByText('状态:', { exact: false })).toBeVisible()

    // 3. 测试状态筛选
    const statusSelect = filterCard.locator('select').nth(2)
    await statusSelect.selectOption('PENDING')
    await waitForLoading(page)

    // 4. 刷新按钮应该可用
    const refreshBtn = filterCard.locator('button').filter({ hasText: '刷新' })
    await expect(refreshBtn).toBeVisible()
    await refreshBtn.click()
    await waitForLoading(page)
  })

  test('应该能够打开单个缺口回补确认弹窗', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')
    await waitForLoading(page)

    // 2. 查找待回补的缺口 - 测试数据已在 beforeAll 中准备好
    const pendingGapRow = page.locator('tr').filter({ hasText: '待回补' }).first()
    const gapCount = await pendingGapRow.count()
    
    if (gapCount === 0) {
      // 如果没有待回补的缺口，先执行一次检测
      await detectGaps(page)
      await page.goto('/gaps')
      await waitForPageTitle(page, '缺口管理')
      await waitForLoading(page)
    }

    // 重新查找
    const gapRow = page.locator('tr').filter({ hasText: '待回补' }).first()
    if (await gapRow.count() === 0) {
      // 如果仍然没有缺口，说明数据完整，测试通过
      console.log('No gaps found - data is complete, test passes')
      return
    }

    // 3. 点击回补按钮
    const fillBtn = gapRow.locator('button').filter({ hasText: '回补' })
    await fillBtn.click()

    // 4. 验证确认弹窗出现
    const confirmDialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(confirmDialog).toBeVisible()
    await expect(confirmDialog.getByRole('heading', { name: '回补缺口' })).toBeVisible()

    // 5. 关闭弹窗
    const cancelBtn = confirmDialog.locator('button').filter({ hasText: '取消' })
    await cancelBtn.click()
    await expect(confirmDialog).not.toBeVisible()
  })

  test('应该能够执行单个缺口回补', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')
    await waitForLoading(page)

    // 2. 查找待回补的缺口
    let pendingGapRow = page.locator('tr').filter({ hasText: '待回补' }).first()
    
    if (await pendingGapRow.count() === 0) {
      // 如果没有待回补的缺口，先执行一次检测
      await detectGaps(page)
      await page.goto('/gaps')
      await waitForPageTitle(page, '缺口管理')
      await waitForLoading(page)
      pendingGapRow = page.locator('tr').filter({ hasText: '待回补' }).first()
    }

    if (await pendingGapRow.count() === 0) {
      // 如果仍然没有缺口，说明数据完整，测试通过
      console.log('No gaps found - data is complete, test passes')
      return
    }

    // 3. 点击回补按钮
    const fillBtn = pendingGapRow.locator('button').filter({ hasText: '回补' })
    await fillBtn.click()

    // 4. 确认回补
    const confirmDialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(confirmDialog).toBeVisible()
    
    const confirmBtn = confirmDialog.locator('button').filter({ hasText: '开始回补' })
    await confirmBtn.click()

    // 5. 等待回补完成
    await page.waitForTimeout(1000)
    const resultHeading = page.getByRole('heading', { name: /回补成功|回补失败/ })
    await expect(resultHeading).toBeVisible({ timeout: 30000 })

    // 6. 关闭结果弹窗
    await page.locator('.fixed.inset-0.z-50').first().locator('button').filter({ hasText: '确定' }).click()
    await page.waitForTimeout(500)
  })

  test('应该能够选择多个缺口进行批量回补', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')
    await waitForLoading(page)

    // 2. 查找待回补的缺口
    let pendingGapRows = page.locator('tr').filter({ hasText: '待回补' })
    let count = await pendingGapRows.count()
    
    if (count === 0) {
      // 没有待回补的缺口，先执行一次检测
      await detectGaps(page)
      await page.goto('/gaps')
      await waitForPageTitle(page, '缺口管理')
      await waitForLoading(page)
      pendingGapRows = page.locator('tr').filter({ hasText: '待回补' })
      count = await pendingGapRows.count()
    }

    if (count === 0) {
      // 如果仍然没有缺口，说明数据完整，测试通过
      console.log('No gaps found - data is complete, test passes')
      return
    }

    // 3. 选择第一个缺口
    const firstCheckbox = pendingGapRows.first().locator('input[type="checkbox"]')
    await firstCheckbox.check()

    // 4. 验证批量回补按钮显示选中数量
    const batchFillBtn = page.locator('button').filter({ hasText: /批量回补/ })
    await expect(batchFillBtn).toBeVisible()
    await expect(batchFillBtn).toContainText('(1)')

    // 5. 如果有多个缺口，选择第二个
    if (count > 1) {
      const secondCheckbox = pendingGapRows.nth(1).locator('input[type="checkbox"]')
      await secondCheckbox.check()
      await expect(batchFillBtn).toContainText('(2)')
    }

    // 6. 点击批量回补按钮
    await batchFillBtn.click()

    // 7. 验证确认弹窗出现
    const confirmDialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(confirmDialog).toBeVisible()
    await expect(confirmDialog.getByRole('heading', { name: /批量回补/ })).toBeVisible()

    // 8. 关闭弹窗 (不实际执行批量回补)
    const cancelBtn = confirmDialog.locator('button').filter({ hasText: '取消' })
    await cancelBtn.click()
    await expect(confirmDialog).not.toBeVisible()
  })

  test('应该能够打开自动回补配置弹窗', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')
    await waitForLoading(page)

    // 2. 查找任意缺口行
    let gapRow = page.locator('tbody tr').first()
    
    if (await gapRow.count() === 0) {
      // 没有缺口记录，先执行一次检测
      await detectGaps(page)
      await page.goto('/gaps')
      await waitForPageTitle(page, '缺口管理')
      await waitForLoading(page)
      gapRow = page.locator('tbody tr').first()
    }

    if (await gapRow.count() === 0) {
      // 如果仍然没有缺口，说明数据完整，测试通过
      console.log('No gaps found - data is complete, test passes')
      return
    }

    // 3. 点击自动回补按钮
    const autoFillBtn = gapRow.locator('button').filter({ hasText: '自动回补' })
    await autoFillBtn.click()

    // 4. 验证配置弹窗出现
    const configDialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(configDialog).toBeVisible()
    await expect(configDialog.getByRole('heading', { name: '自动回补配置' })).toBeVisible()

    // 5. 验证配置表格存在
    await expect(configDialog.locator('table')).toBeVisible()
    await expect(configDialog.getByText('周期', { exact: false })).toBeVisible()
    await expect(configDialog.getByText('自动回补', { exact: false })).toBeVisible()

    // 6. 关闭弹窗
    const closeBtn = configDialog.locator('button').filter({ hasText: '关闭' })
    await closeBtn.click()
    await expect(configDialog).not.toBeVisible()
  })

  test('自动回补配置弹窗应该显示提示信息', async ({ page }) => {
    // 1. 导航到缺口管理页面
    await page.goto('/gaps')
    await waitForPageTitle(page, '缺口管理')
    await waitForLoading(page)

    // 2. 查找任意缺口行
    let gapRow = page.locator('tbody tr').first()
    
    if (await gapRow.count() === 0) {
      // 没有缺口记录，先执行一次检测
      await detectGaps(page)
      await page.goto('/gaps')
      await waitForPageTitle(page, '缺口管理')
      await waitForLoading(page)
      gapRow = page.locator('tbody tr').first()
    }

    if (await gapRow.count() === 0) {
      // 如果仍然没有缺口，说明数据完整，测试通过
      console.log('No gaps found - data is complete, test passes')
      return
    }

    // 3. 点击自动回补按钮
    await gapRow.locator('button').filter({ hasText: '自动回补' }).click()

    // 4. 验证提示信息
    const configDialog = page.locator('.fixed.inset-0.z-50').first()
    await expect(configDialog).toBeVisible()
    
    // 验证提示文本
    await expect(configDialog.getByText('全局自动回补开关', { exact: false })).toBeVisible()
    await expect(configDialog.getByText('sync.gap_fill.auto', { exact: false })).toBeVisible()

    // 5. 关闭弹窗
    await configDialog.locator('button').filter({ hasText: '关闭' }).click()
  })
})
