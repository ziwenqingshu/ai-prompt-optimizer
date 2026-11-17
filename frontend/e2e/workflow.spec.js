import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'

const fixture = 'http://127.0.0.1:18993'

test.beforeEach(async ({ request, page }) => {
  await request.post(`${fixture}/control`, { data: {} })
  const history = await (await request.get('/api/prompts/history')).json()
  for (const item of history) await request.delete(`/api/prompts/history/${item.id}`)
  await page.goto('/')
  await expect(page.getByText('模型接口已配置')).toBeVisible()
})

for (const [scene, style] of [['SUMMARY', 'CONCISE'], ['CODE', 'PROFESSIONAL'], ['RESUME', 'DETAILED'], ['GENERAL', 'PROFESSIONAL']]) {
  test(`${scene}: optimize, copy, rate, reload, restore and delete`, async ({ page }) => {
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.getByLabel('原始提示词', { exact: true }).fill(`浏览器测试 ${scene}：请帮我完善任务要求`)
    await page.getByLabel('使用场景').selectOption(scene)
    await page.getByLabel('输出风格').selectOption(style)
    await page.getByRole('button', { name: '开始优化' }).click()
    await expect(page.locator('.result-card')).toBeVisible()
    await expect(page.locator('.result-card ol li')).toHaveCount(3)
    await expect(page.locator('.result-card .badge')).toContainText('e2e-fixture-not-real')
    await page.getByRole('button', { name: '复制提示词' }).click()
    await expect(page.getByRole('button', { name: '已复制' })).toBeVisible()
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(await page.locator('.result-card pre').innerText())
    await page.getByRole('button', { name: '5 分', exact: true }).click()
    await expect(page.getByRole('button', { name: '5 分', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await page.reload()
    await expect(page.locator('.history-item')).toHaveCount(1)
    await page.locator('.history-item').click()
    await expect(page.getByLabel('使用场景')).toHaveValue(scene)
    await expect(page.getByLabel('输出风格')).toHaveValue(style)
    await expect(page.getByRole('button', { name: '5 分', exact: true })).toHaveAttribute('aria-pressed', 'true')
    // Keyboard deletion must not bubble into the history restore handler.
    await page.getByRole('button', { name: '删除', exact: true }).focus()
    await page.keyboard.press('Enter')
    await page.getByRole('button', { name: '确认删除', exact: true }).click()
    await expect(page.locator('.history-item')).toHaveCount(0)
    await expect(page.locator('.result-card')).toHaveCount(0)
    await page.reload()
    await expect(page.locator('.history-item')).toHaveCount(0)
    expect(errors).toEqual([])
  })
}

test('validation and model connection probe do not create history', async ({ page, request }) => {
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.getByRole('alert')).toHaveText('请先输入原始提示词。')
  expect((await (await request.get(`${fixture}/stats`)).json()).calls).toBe(0)
  await page.locator('.model-settings > summary').click()
  await page.getByRole('button', { name: '检测连接' }).click()
  await expect(page.getByRole('status')).toContainText('连接成功')
  expect(await (await request.get('/api/prompts/history')).json()).toEqual([])
  const status = await (await request.get('/api/model/status')).text()
  expect(status).not.toContain('e2e-fake-secret')
})

test('auth error and timeout show actionable errors, then retry succeeds', async ({ page, request }) => {
  await page.getByLabel('原始提示词', { exact: true }).fill('请帮我设计一个学习计划')
  await request.post(`${fixture}/control`, { data: { status: 401 } })
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.getByRole('alert')).toContainText('鉴权失败')
  expect(await page.locator('body').innerText()).not.toContain('e2e-fake-secret')
  await expect(page.locator('.history-item')).toHaveCount(0)
  await request.post(`${fixture}/control`, { data: { delay: 3000 } })
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.getByRole('button', { name: '正在优化…' })).toBeDisabled()
  await expect(page.getByRole('alert')).toContainText('超时')
  await expect(page.locator('.history-item')).toHaveCount(0)
  await request.post(`${fixture}/control`, { data: {} })
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.locator('.result-card')).toBeVisible()
})

test('history and rating survive full service restart', async ({ page, request }) => {
  await page.getByLabel('原始提示词', { exact: true }).fill('验证服务重启后的历史持久化')
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.locator('.result-card')).toBeVisible()
  await page.getByRole('button', { name: '4 分', exact: true }).click()
  await expect(page.getByRole('button', { name: '4 分', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await request.post(`${fixture}/restart`)
  await expect.poll(async () => {
    try { return (await request.get('/api/health', { timeout: 1000 })).status() } catch { return 0 }
  }, { timeout: 45000, intervals: [1000] }).toBe(200)
  await page.reload()
  await expect(page.locator('.history-item')).toHaveCount(1)
  await page.locator('.history-item').click()
  await expect(page.getByRole('button', { name: '4 分', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('mobile layout and degraded output remain usable', async ({ page, request }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await request.post(`${fixture}/control`, { data: { content: '请明确目标、输入和约束，并以列表输出。' } })
  await page.getByLabel('原始提示词', { exact: true }).fill('帮我改善这段提示词')
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.getByText('模型格式异常，结果已降级展示。')).toBeVisible()
  await expect(page.locator('.result-card ol li')).toHaveCount(3)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})


test('acceptance script verifies protocol against fixture (not real inference)', async ({ request }) => {
  const output = execFileSync('python3', ['../scripts/verify-live.py'], {
    env: { ...process.env, BACKEND_URL: 'http://127.0.0.1:18081' }, encoding: 'utf8', timeout: 10000
  })
  const report = JSON.parse(output)
  expect(report.passed).toBe(true)
  expect(report.model).toBe('e2e-fixture-not-real')
  expect(await (await request.get('/api/prompts/history')).json()).toEqual([])
})


test('example and browser draft recover after refresh; reset can be cancelled', async ({ page }) => {
  await page.getByLabel('使用场景').selectOption('CODE')
  await page.getByRole('button', { name: '试用示例' }).click()
  await expect(page.getByLabel('原始提示词', { exact: true })).toHaveValue(/CSV/)
  await page.getByLabel('原始提示词', { exact: true }).fill('我还没有完成的草稿')
  await page.getByLabel('输出风格').selectOption('DETAILED')
  await page.reload()
  await expect(page.getByLabel('原始提示词', { exact: true })).toHaveValue('我还没有完成的草稿')
  await expect(page.getByLabel('使用场景')).toHaveValue('CODE')
  await expect(page.getByLabel('输出风格')).toHaveValue('DETAILED')
  await page.getByRole('button', { name: '新建提示词' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByLabel('原始提示词', { exact: true })).toHaveValue('我还没有完成的草稿')
  await page.getByRole('button', { name: '新建提示词' }).click()
  await page.getByRole('button', { name: '新建', exact: true }).click()
  await expect(page.getByLabel('原始提示词', { exact: true })).toBeEmpty()
  await page.reload()
  await expect(page.getByLabel('原始提示词', { exact: true })).toBeEmpty()
})

test('search, scene filtering, keyboard history restore and cancelled delete', async ({ page, request }) => {
  for (const [scene, originalPrompt] of [['CODE', '开发排序算法'], ['SUMMARY', '总结会议记录']]) {
    await request.post('/api/prompts/optimize', { data: { originalPrompt, scene, style: 'PROFESSIONAL' } })
  }
  await page.reload()
  await expect(page.locator('.history-item')).toHaveCount(2)
  await page.getByLabel('搜索历史记录').fill('排序')
  await expect(page.locator('.history-item')).toHaveCount(1)
  await page.getByLabel('筛选历史场景').selectOption('SUMMARY')
  await expect(page.getByText('没有匹配的记录')).toBeVisible()
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(page.locator('.history-item')).toHaveCount(2)
  await page.locator('.history-item').first().focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('.result-card')).toBeVisible()
  await page.getByRole('button', { name: '删除', exact: true }).first().click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.locator('.history-item')).toHaveCount(2)
})

test('keyboard submit, previous-result preservation and Markdown export use original source', async ({ page, request }) => {
  await page.getByLabel('原始提示词', { exact: true }).fill('原始需求：制定学习计划')
  await page.getByLabel('原始提示词', { exact: true }).press('Control+Enter')
  await expect(page.locator('.result-card')).toBeVisible()
  const originalResult = await page.locator('.result-card pre').innerText()
  await page.getByLabel('原始提示词', { exact: true }).fill('尚未成功优化的新需求')
  await expect(page.getByText('输入已更改。下方仍为上次结果，重新优化后更新。')).toBeVisible()
  await request.post(`${fixture}/control`, { data: { status: 401 } })
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.getByRole('alert')).toContainText('鉴权失败')
  await expect(page.locator('.result-card pre')).toHaveText(originalResult)
  const downloadEvent = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 Markdown' }).click()
  const download = await downloadEvent
  expect(download.suggestedFilename()).toMatch(/\.md$/)
  const stream = await download.createReadStream()
  const chunks = []
  for await (const chunk of stream) chunks.push(chunk)
  const markdown = Buffer.concat(chunks).toString('utf8')
  expect(markdown).toContain('原始需求：制定学习计划')
  expect(markdown).not.toContain('尚未成功优化的新需求')
  expect(markdown).toContain('## 改进建议')
})

test('replacing a draft by an example or history requires explicit action', async ({ page, request }) => {
  await request.post('/api/prompts/optimize', { data: { originalPrompt: '已有历史记录', scene: 'GENERAL', style: 'CONCISE' } })
  await page.reload()
  await page.getByLabel('原始提示词', { exact: true }).fill('当前重要草稿')
  await page.getByRole('button', { name: '试用示例' }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByLabel('原始提示词', { exact: true })).toHaveValue('当前重要草稿')
  await page.locator('.history-item').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: '打开记录', exact: true }).click()
  await expect(page.getByLabel('原始提示词', { exact: true })).toHaveValue('已有历史记录')
})

test('history load failure has a working retry; storage disabled does not block editing', async ({ page }) => {
  await page.addInitScript(() => { Storage.prototype.setItem = () => { throw new Error('disabled') } })
  await page.route('**/api/prompts/history', route => route.fulfill({ status: 503, body: '{}' }))
  await page.reload()
  await expect(page.getByText('历史记录加载失败，请重试。')).toBeVisible()
  await page.unroute('**/api/prompts/history')
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('历史记录加载失败，请重试。')).toHaveCount(0)
  await page.getByLabel('原始提示词', { exact: true }).fill('仍然可以使用的输入')
  await expect(page.getByText('草稿未保存，请保留当前页面')).toBeVisible()
  await page.getByRole('button', { name: '开始优化' }).click()
  await expect(page.locator('.result-card')).toBeVisible()
})


test('mobile shortcuts reach history and restore the result into view', async ({ page, request }) => {
  await request.post('/api/prompts/optimize', { data: { originalPrompt: '手机端历史记录', scene: 'GENERAL', style: 'CONCISE' } })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await page.getByRole('link', { name: /历史记录 ·/ }).click()
  await expect(page.locator('.history-item')).toBeInViewport()
  await page.locator('.history-item').click()
  await expect(page.locator('.result-card')).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '新建提示词' }).click()
  await expect(page.getByLabel('原始提示词', { exact: true })).toBeEmpty()
})
