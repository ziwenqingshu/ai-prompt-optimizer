// Deterministic HTTP fixture, NOT a real model. Uses isolated data and fake credentials.
import http from 'node:http'
import { spawn } from 'node:child_process'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const dir = mkdtempSync(join(tmpdir(), 'prompt-optimizer-e2e-'))
const root = resolve('..')
const config = join(dir, '.env')
const defaultContent = JSON.stringify({ optimizedPrompt: '请明确目标、输入与约束，以 Markdown 格式输出可执行的方案，并说明验证步骤。', suggestions: ['明确目标', '补充约束', '规定输出格式'] })
let behavior = {}
let calls = 0
let app
let closing = false
let stopping = false
writeFileSync(config, `LLM_MODE=live\nLLM_MODEL=e2e-fixture-not-real\nLLM_BASE_URL=http://127.0.0.1:18993/v1\nLLM_API_KEY=e2e-fake-secret\nLLM_JSON_MODE=true\nLLM_READ_TIMEOUT_SECONDS=2\nAPP_DATA_DIR=${dir}/data\nBACKEND_PORT=18081\nFRONTEND_PORT=15173\n`, { mode: 0o600 })
const env = { ...process.env }
for (const key of Object.keys(env)) {
  if (/^(LLM_|APP_|BACKEND_PORT$|FRONTEND_PORT$|HOST$|SERVER_|PROMPT_OPTIMIZER_ENV_LOADED$|SPRING_APPLICATION_JSON$)/.test(key)) delete env[key]
}
env.APP_ENV_FILE = config
function startApp() {
  app = spawn('bash', [join(root, 'scripts/run-local.sh')], { cwd: root, env, detached: true, stdio: 'inherit' })
  app.on('exit', code => { if (!closing && !stopping && code) console.error(`Test app exited: ${code}`) })
}
async function stopApp() {
  if (!app || app.exitCode !== null || app.signalCode !== null) return
  stopping = true
  const child = app
  const done = new Promise(resolve => child.once('exit', resolve))
  try { process.kill(-child.pid, 'SIGTERM') } catch {}
  await done
  stopping = false
}
const server = http.createServer(async (req, res) => {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const raw = Buffer.concat(chunks).toString()
  res.setHeader('Content-Type', 'application/json')
  if (req.url === '/control' && req.method === 'POST') {
    behavior = JSON.parse(raw || '{}'); calls = 0
    return res.end('{}')
  }
  if (req.url === '/stats') return res.end(JSON.stringify({ calls }))
  if (req.url === '/restart' && req.method === 'POST') {
    await stopApp(); startApp()
    return res.end('{}')
  }
  if (req.url !== '/v1/chat/completions') { res.statusCode = 404; return res.end('{}') }
  calls++
  const body = JSON.parse(raw)
  if (req.headers.authorization !== 'Bearer e2e-fake-secret' || body.model !== 'e2e-fixture-not-real'
      || body.messages?.length !== 2 || body.response_format?.type !== 'json_object') {
    res.statusCode = 400
    return res.end('{}')
  }
  const current = { ...behavior }
  if (current.delay) await new Promise(resolve => setTimeout(resolve, current.delay))
  res.statusCode = current.status || 200
  res.end(current.status ? JSON.stringify({ error: 'e2e-fake-secret' }) : JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content: current.content ?? defaultContent } }] }))
})
server.listen(18993, '127.0.0.1', startApp)
async function cleanup() {
  if (closing) return
  closing = true
  await stopApp()
  server.close()
  rmSync(dir, { recursive: true, force: true })
  process.exit(0)
}
process.on('SIGTERM', cleanup)
process.on('SIGINT', cleanup)
