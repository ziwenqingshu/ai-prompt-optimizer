# 智能提示词优化助手

一个面向提示词优化场景的全栈 AI 应用。用户输入原始提示词、使用场景和输出风格，系统返回优化后的提示词与三条改进建议。

项目用于展示一条完整的交付链路：Vue 页面 → Java REST API → OpenAI 兼容大模型接口 → Docker Compose 部署。

## 已实现功能

- 单页 Vue 交互页面，支持文本输入、场景和风格选择、结果复制、加载与错误提示
- Java Spring Boot REST API，提供输入校验和统一错误响应
- 提示词模板：文本总结、代码助手、简历优化、通用问答四种场景；简洁、专业、详细三种风格
- `mock` 模式：不需要 API Key，可用于前后端联调
- `live` 模式：通过服务端调用 OpenAI 兼容模型接口，API Key 不会传到浏览器
- 模型输出容错：解析 JSON、清理代码围栏、补齐或截断建议；模型格式异常时仍返回稳定响应
- Nginx 反向代理，Spring Boot 后端不直接暴露到宿主机；优化接口按 IP 限流
- Docker Compose 一键启动

## 技术栈

- 后端：Java 21、Spring Boot、Spring Validation、Jackson
- 前端：Vue 3、Vite、原生 Fetch
- 部署：Nginx、Docker Compose
- 模型：OpenAI 兼容接口，示例配置使用 DeepSeek

## 项目结构

```text
.
├─ backend/                 # Spring Boot API 与单元测试
├─ frontend/                # Vue 页面与 Nginx 配置
├─ docker-compose.yml       # 前后端容器编排
├─ .env.example             # 不含密钥的环境变量模板
└─ README.md
```

## 本地运行

前置条件：Docker Desktop 与 Docker Compose 可用。

```powershell
Copy-Item .env.example .env
docker compose -p ai-prompt-optimizer up -d --build
```

访问 [http://localhost](http://localhost)。默认是 `mock` 模式，不依赖外部模型或 API Key。

查看状态：

```powershell
docker compose -p ai-prompt-optimizer ps
```

停止服务：

```powershell
docker compose -p ai-prompt-optimizer down
```

## 启用真实模型

需要访问模型 API，请先确保网络或代理可用。只编辑本地或服务器上的 `.env`，不要提交该文件。

```dotenv
LLM_MODE=live
LLM_BASE_URL=https://api.deepseek.com
LLM_API_KEY=你的API_KEY
LLM_MODEL=你有权限使用的模型名称
LLM_JSON_MODE=true
```

修改后重建容器：

```powershell
docker compose -p ai-prompt-optimizer up -d --no-build --force-recreate
```

项目会请求 JSON 输出，但不会只依赖模型的 JSON 模式；后端仍会校验、归一化和降级处理输出。

## API

### 健康检查

```http
GET /api/health
```

```json
{"status":"UP"}
```

### 优化提示词

```http
POST /api/prompts/optimize
Content-Type: application/json
```

```json
{
  "originalPrompt": "帮我优化一份 Java 后端实习简历",
  "scene": "RESUME",
  "style": "PROFESSIONAL"
}
```

`scene` 可选：`SUMMARY`、`CODE`、`RESUME`、`GENERAL`。

`style` 可选：`CONCISE`、`PROFESSIONAL`、`DETAILED`。

成功响应：

```json
{
  "optimizedPrompt": "...",
  "suggestions": ["...", "...", "..."],
  "model": "...",
  "mode": "LIVE",
  "degraded": false
}
```

错误响应示例：

```json
{"error":"原始提示词不能为空","code":"VALIDATION_ERROR"}
```

## 服务器部署

服务器需要提供：公网 IP、SSH 登录方式、Docker Compose、可开放的 HTTP 端口，以及访问 Docker Hub、Maven Central、npm registry 和模型 API 的网络条件。若服务器访问这些外部资源受限，需要配置代理或镜像加速。

以下命令假定服务器是常见的 Linux Docker 主机：

```bash
git clone <你的GitHub仓库地址>
cd <项目目录>
cp .env.example .env
# 按实际需要编辑 .env，配置 LLM_MODE 与 LLM_API_KEY
docker compose -p ai-prompt-optimizer up -d --build
```

默认映射主机 `80` 端口。部署完成后访问：

```text
http://服务器公网IP/
http://服务器公网IP/api/health
```

## 已验证内容

- 后端 Maven 依赖解析、Java 编译、JUnit 测试和 JAR 打包通过
- 前端 Vite 生产构建通过
- 前后端 Docker 镜像构建通过
- Compose 启动后，后端健康检查为 `UP`
- 浏览器页面提交、`mock` 模式、参数校验错误响应已验证
- `live` 模式已完成一次真实模型调用验证

## 当前边界

- 这是单用户提示词优化工具，不包含账号、历史记录、数据库或文件上传
- Nginx 已为优化接口配置基础 IP 限流；正式生产环境仍应根据实际用户量、鉴权和成本控制需求继续完善
- 当前限流适用于服务器公网 IP 直连；若接入 Cloudflare 或其他上游反向代理，须按实际代理来源配置可信 IP 与真实客户端 IP 解析，否则可能误限流
- 域名、HTTPS 证书、服务器防火墙和 API Key 由部署环境负责配置

## 安全约定

- `.env` 已在 `.gitignore` 中，禁止提交 API Key
- 后端容器仅在 Docker 内部网络暴露 `8080`；公网入口为 Nginx
- 不在日志、README、截图或 Git 提交中保存真实 API Key
