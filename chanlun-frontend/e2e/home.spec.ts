import { test, expect } from '@playwright/test'

test.describe('首页', () => {
  test('应该正确加载首页', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/缠论交易分析平台/)
  })

  test('应该显示导航菜单', async ({ page }) => {
    await page.goto('/')
    // 检查侧边栏导航是否存在
    await expect(page.locator('nav')).toBeVisible()
  })
})
