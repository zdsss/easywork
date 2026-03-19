/**
 * EasyWork 系统页面截图脚本（完整版）
 */
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const ADMIN_URL = 'http://localhost:5177';
const WORKER_URL = 'http://localhost:5178';
const SCREENSHOTS_DIR = path.join(__dirname, 'screenshots');

if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

const executablePath = 'C:\\Users\\Administrator\\utils\\.playwright\\chromium-1208\\chrome-win64\\chrome.exe';

// Admin JWT (admin/admin123)
const ADMIN_TOKEN = 'eyJhbGciOiJIUzM4NCJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6IkFETUlOMDAxIiwiaWF0IjoxNzczODI4NTA3LCJleHAiOjE3NzM5MTQ5MDd9.0IbLSA0IMCbkZAPg_pooDgwdsJ3tUTvVZ6Is5DeHGJgRL2YrtO1qGuFqh8GfwWZI';
// Worker JWT (WORKER001/worker123)
const WORKER_TOKEN = 'eyJhbGciOiJIUzM4NCJ9.eyJyb2xlIjoiV09SS0VSIiwidXNlcklkIjoxMjcsInN1YiI6IldPUktFUjAwMSIsImlhdCI6MTc3MzgyODc2MywiZXhwIjoxNzczOTE1MTYzfQ.6QVijct1wRWdZaz4oiUutEqqERFxhoMx6KjbLVSKenvF7zK0RiiKIUkG82AAtW7b';

async function ss(page, filename, description, delay = 1500) {
  await page.waitForTimeout(delay);
  const fp = path.join(SCREENSHOTS_DIR, filename);
  await page.screenshot({ path: fp, fullPage: true });
  console.log(`  ✓ [${filename}] ${description}`);
}

async function injectAdminToken(page) {
  await page.goto(`${ADMIN_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.evaluate((token) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify({ userId: 1, role: 'ADMIN', employeeNumber: 'ADMIN001', realName: 'System Admin' }));
  }, ADMIN_TOKEN);
}

async function injectWorkerToken(page) {
  await page.goto(`${WORKER_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.evaluate((token) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify({ userId: 127, role: 'WORKER', employeeNumber: 'WORKER001', realName: 'Worker One' }));
  }, WORKER_TOKEN);
}

async function screenshotAdmin(browser) {
  console.log('\n===== 管理端截图 =====');
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1440, height: 900 });

  try {
    // 登录页
    await page.goto(`${ADMIN_URL}/login`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '01-admin-login.png', '管理端登录页', 1000);

    // 注入 token 后跳转
    await injectAdminToken(page);

    // 看板
    await page.goto(`${ADMIN_URL}/dashboard`, { waitUntil: 'networkidle', timeout: 15000 });
    await ss(page, '02-admin-dashboard.png', '统计看板 (ECharts)', 3000);

    // 工单列表
    await page.goto(`${ADMIN_URL}/workorders`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '03-admin-workorder-list.png', '工单列表', 2000);

    // 工单详情（ID 207）
    await page.goto(`${ADMIN_URL}/workorders/207`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '04-admin-workorder-detail.png', '工单详情（含工序状态）', 2000);

    // 创建工单
    await page.goto(`${ADMIN_URL}/workorders/create`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '05-admin-workorder-create.png', '创建工单页', 2000);

    // 质检管理
    await page.goto(`${ADMIN_URL}/inspection`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '06-admin-inspection.png', '质检管理', 2000);

    // 呼叫管理
    await page.goto(`${ADMIN_URL}/calls`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '07-admin-calls.png', 'Andon呼叫管理', 2000);

    // 用户管理
    await page.goto(`${ADMIN_URL}/users`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '08-admin-users.png', '用户管理', 2000);

    // 班组管理
    await page.goto(`${ADMIN_URL}/teams`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '09-admin-teams.png', '班组管理', 2000);

    // 审计日志
    await page.goto(`${ADMIN_URL}/audit-logs`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '10-admin-audit.png', '审计日志', 2000);

    // MES集成
    await page.goto(`${ADMIN_URL}/mes`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '11-admin-mes.png', 'MES集成监控', 2000);

  } catch (err) {
    console.log('  ✗ 管理端错误:', err.message);
  } finally {
    await page.close();
  }
}

async function screenshotWorker(browser) {
  console.log('\n===== 工人端截图 =====');
  const page = await browser.newPage();
  await page.setViewportSize({ width: 390, height: 844 });

  try {
    // 登录页（未登录状态）
    await page.goto(`${WORKER_URL}/login`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '12-worker-login.png', '工人端登录页（T9键盘）', 1000);

    // 注入 worker token
    await injectWorkerToken(page);

    // 工单列表
    await page.goto(`${WORKER_URL}/workorders`, { waitUntil: 'networkidle', timeout: 15000 });
    await ss(page, '13-worker-workorder-list.png', '工人端工单列表', 2000);

    // 工单详情
    await page.goto(`${WORKER_URL}/workorders/207`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '14-worker-workorder-detail.png', '工人端工单详情（开工/报工操作）', 2000);

    // 扫码页
    await page.goto(`${WORKER_URL}/scan`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '15-worker-scan.png', '扫码开工/报工页', 2000);

    // 呼叫页
    await page.goto(`${WORKER_URL}/call`, { waitUntil: 'networkidle', timeout: 10000 });
    await ss(page, '16-worker-call.png', 'Andon/质检/搬运呼叫页', 2000);

  } catch (err) {
    console.log('  ✗ 工人端错误:', err.message);
  } finally {
    await page.close();
  }
}

async function main() {
  console.log('启动 Chromium...');
  const browser = await chromium.launch({
    headless: true,
    executablePath,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });
  try {
    await screenshotAdmin(browser);
    await screenshotWorker(browser);

    const files = fs.readdirSync(SCREENSHOTS_DIR).filter(f => f.endsWith('.png')).sort();
    console.log(`\n===== 完成 ${files.length} 张截图 =====`);
    files.forEach(f => console.log(`  ${f}`));
  } finally {
    await browser.close();
  }
}

main().catch(err => { console.error('[错误]', err.message); process.exit(1); });
