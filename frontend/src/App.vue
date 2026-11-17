<script setup>
import UiIcon from "./components/UiIcon.vue";
import {
  usePromptWorkspace,
  scenes,
  styles,
  sceneLabel,
  styleLabel,
} from "./composables/usePromptWorkspace";
const {
  form,
  result,
  source,
  history,
  query,
  sceneFilter,
  error,
  historyError,
  resultError,
  loading,
  historyLoading,
  copied,
  notice,
  draftState,
  modelStatus,
  connectionMessage,
  connectionError,
  testingConnection,
  busy,
  count,
  changed,
  filteredHistory,
  activeExample,
  dialog,
  confirmation,
  editor,
  resultPanel,
  loadHistory,
  loadModelStatus,
  testConnection,
  formatDate,
  cancelConfirmation,
  confirmAction,
  newPrompt,
  useExample,
  optimize,
  selectHistory,
  requestDelete,
  rateResult,
  copyPrompt,
  exportResult,
} = usePromptWorkspace();
</script>

<template>
  <a class="skip-link" href="#original-prompt">跳到提示词输入</a>
  <div class="app-layout">
    <aside id="history" class="sidebar" aria-label="提示词历史记录">
      <a class="brand" href="#workspace" aria-label="Prompt Studio 工作台"
        ><span class="brand-mark"><UiIcon name="spark" :size="23" /></span
        ><span>Prompt Studio<small>你的提示词工作台</small></span></a
      >
      <button class="new-button" :disabled="busy" @click="newPrompt">
        <UiIcon name="plus" />新建提示词<span class="key-hint">+</span>
      </button>
      <div class="sidebar-heading">
        <h2><UiIcon name="clock" :size="16" />最近记录</h2>
        <span class="count-pill">{{ history.length }}</span>
      </div>
      <div class="search-field">
        <UiIcon name="search" :size="16" /><input
          v-model="query"
          type="search"
          aria-label="搜索历史记录"
          placeholder="搜索提示词…"
        />
      </div>
      <label class="sr-only" for="history-scene">筛选历史场景</label>
      <select id="history-scene" v-model="sceneFilter" class="history-filter">
        <option value="">全部场景</option>
        <option v-for="scene in scenes" :key="scene.value" :value="scene.value">
          {{ scene.label }}
        </option>
      </select>
      <div v-if="historyError" class="history-error" role="alert">
        {{ historyError
        }}<button
          class="text-button"
          :disabled="historyLoading || busy"
          @click="loadHistory"
        >
          重新加载
        </button>
      </div>
      <p v-if="historyLoading" class="sidebar-empty">正在加载记录…</p>
      <div v-else-if="!history.length && !historyError" class="sidebar-empty">
        <UiIcon name="file" :size="28" />
        <p>好提示词，值得留下。</p>
        <span>完成一次优化后，<br />就能在这里找到它。</span>
      </div>
      <div
        v-else-if="!filteredHistory.length && !historyError"
        class="sidebar-empty"
      >
        <p>没有匹配的记录</p>
        <button
          class="text-button"
          @click="
            query = '';
            sceneFilter = '';
          "
        >
          清除筛选
        </button>
      </div>
      <ul v-else class="history-list">
        <li
          v-for="item in filteredHistory"
          :key="item.id"
          :class="{ active: result?.historyId === item.id }"
        >
          <button
            class="history-item"
            :disabled="busy"
            :aria-current="result?.historyId === item.id ? 'true' : undefined"
            @click="selectHistory(item)"
          >
            <span class="history-topline"
              ><span>{{ sceneLabel(item.scene) }}</span
              ><small>{{ formatDate(item.createdAt) }}</small></span
            >
            <span class="history-preview">{{ item.originalPrompt }}</span>
            <span class="history-meta"
              >{{ item.mode === "MOCK" ? "模拟模式" : "模型接口"
              }}<span v-if="item.rating">
                · {{ item.rating }} 分评价</span
              ></span
            >
          </button>
          <button
            class="delete-link"
            :disabled="busy"
            aria-label="删除"
            title="删除记录"
            @click="requestDelete(item)"
          >
            <UiIcon name="trash" :size="15" />
          </button>
        </li>
      </ul>
      <div class="sidebar-footer">
        <UiIcon name="monitor" :size="17" />
        <div>单机工作区<small>无需账户 · 本地保存最近 100 条</small></div>
      </div>
    </aside>

    <main id="workspace" class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          工作台<UiIcon name="chevron" :size="13" /><span>提示词优化</span>
        </div>
        <div class="mode-indicator">
          <span :class="['status-dot', { offline: connectionError }]" />{{
            modelStatus
              ? modelStatus.mode === "MOCK"
                ? "模拟模式"
                : modelStatus.configured
                  ? "模型接口已配置"
                  : "模型待配置"
              : "正在连接"
          }}
        </div>
      </header>
      <div class="workspace-content">
        <section class="hero">
          <p class="eyebrow">从一个想法，到一个好提示词</p>
          <h1>把想法，写得更清楚<span>。</span></h1>
          <p>补全目标、理清约束，让每一次提问都有明确的方向。</p>
        </section>
        <nav class="mobile-toolbar" aria-label="工作区快捷操作">
          <button class="secondary" :disabled="busy" @click="newPrompt">
            <UiIcon name="plus" :size="16" />新建提示词
          </button>
          <a class="secondary" href="#history"
            ><UiIcon name="clock" :size="16" />历史记录 ·
            {{ history.length }}</a
          >
        </nav>
        <div class="mode-note" v-if="modelStatus?.mode === 'MOCK'">
          <UiIcon name="help" :size="17" />
          <p>
            当前为模拟模式，可体验完整流程。结果由固定模板生成，尚未调用真实模型。
          </p>
        </div>
        <details
          class="model-settings"
          :open="connectionError || modelStatus?.configured === false"
        >
          <summary>
            <span><UiIcon name="settings" :size="16" />模型与连接</span
            ><span class="muted"
              >查看配置与状态<UiIcon name="chevron" :size="13"
            /></span>
          </summary>
          <div class="model-settings-body">
            <p v-if="modelStatus">
              {{ modelStatus.mode === "MOCK" ? "模拟模式" : "真实模型模式" }} ·
              {{ modelStatus.model }}
            </p>
            <p class="muted">
              {{ modelStatus?.message || "暂未读取到模型状态" }}
            </p>
            <div class="button-row">
              <button
                class="secondary small"
                :disabled="
                  busy ||
                  !modelStatus?.configured ||
                  modelStatus?.mode === 'MOCK'
                "
                @click="testConnection"
              >
                {{
                  testingConnection ? "正在检测…" : "检测连接"
                }}</button><button
                class="text-button"
                :disabled="busy"
                @click="loadModelStatus"
              >
                刷新状态
              </button>
            </div>
            <p class="settings-help">
              需要使用真实模型时，在项目 .env
              中配置服务地址、模型名和密钥，并重启服务。检测连接会发送简短请求，可能产生模型费用，不写入历史。
            </p>
            <p
              v-if="connectionMessage"
              :class="['message', connectionError ? 'error' : 'success']"
              role="status"
            >
              {{ connectionMessage }}
            </p>
          </div>
        </details>

        <div class="editor-grid">
          <section class="card input-card" aria-label="提示词优化表单">
            <div class="card-heading">
              <div class="section-title">
                <span class="step">01</span>
                <h2>描述你的需求</h2>
              </div>
              <span class="muted">输入</span>
            </div>
            <div class="card-body">
              <div class="selectors">
                <label
                  >使用场景<select v-model="form.scene" :disabled="busy">
                    <option
                      v-for="scene in scenes"
                      :key="scene.value"
                      :value="scene.value"
                    >
                      {{ scene.label }}
                    </option>
                  </select></label
                ><label
                  >输出风格<select v-model="form.style" :disabled="busy">
                    <option
                      v-for="style in styles"
                      :key="style.value"
                      :value="style.value"
                    >
                      {{ style.label }}
                    </option>
                  </select></label
                >
              </div>
              <div class="label-row">
                <label for="original-prompt">原始提示词</label
                ><button
                  class="text-button example-button"
                  :disabled="busy"
                  @click="useExample"
                >
                  <UiIcon :name="activeExample.icon" :size="14" />试用示例
                </button>
              </div>
              <textarea
                id="original-prompt"
                ref="editor"
                v-model="form.originalPrompt"
                :disabled="busy"
                maxlength="2000"
                rows="7"
                placeholder="你想让 AI 帮你做什么？&#10;&#10;写下任务、背景和期望的结果。即使只有一个简单的想法，也可以从这里开始。"
                :aria-invalid="!!error"
                aria-describedby="input-hint input-error"
              />
              <div class="input-meta">
                <span id="input-hint">{{ draftState }}</span><span
                  :class="{ 'near-limit': count > 1800 }"
                  >{{ count }} / 2000</span
                >
              </div>
              <div class="writing-tip">
                <UiIcon name="spark" :size="17" />
                <p>
                  试着补充：<strong>做什么</strong>、<strong>给谁看</strong>、<strong>怎样输出</strong>。
                </p>
              </div>
              <p
                v-if="error"
                id="input-error"
                class="message error"
                role="alert"
              >
                {{ error }}
              </p>
              <button
                class="primary optimize-button"
                :disabled="busy || modelStatus?.configured === false"
                @click="optimize"
              >
                <span
                  v-if="loading"
                  class="spinner"
                  aria-hidden="true"
                /><UiIcon v-else name="spark" :size="18" />{{
                  loading ? "正在优化…" : "开始优化"
                }}<UiIcon v-if="!loading" name="arrow" :size="18" />
              </button>
              <p class="shortcut-hint">
                支持 <kbd>Ctrl</kbd> / <kbd>⌘</kbd> + <kbd>Enter</kbd> 快速优化
              </p>
            </div>
          </section>

          <section
            ref="resultPanel"
            tabindex="-1"
            :class="['card output-card', { 'result-card': result }]"
            aria-label="优化结果"
            :aria-busy="loading"
          >
            <div class="card-heading">
              <div class="section-title">
                <span class="step result-step">02</span>
                <h2>优化后的提示词</h2>
              </div>
              <span v-if="result" class="saved-label"
                ><UiIcon name="check" :size="14" />已保存</span
              ><span v-else class="muted">输出</span>
            </div>
            <div v-if="loading" class="loading-banner" role="status">
              <span class="spinner" />正在整理目标、约束与输出格式…
            </div>
            <div v-if="!result" class="result-empty">
              <span class="empty-art"><UiIcon name="spark" :size="34" /></span>
              <h3>
                {{
                  loading ? "正在打磨你的提示词" : "好结果，从清晰的表达开始"
                }}
              </h3>
              <p>
                在左侧输入需求，点击「开始优化」。<br />你的提示词和改进建议将显示在这里。
              </p>
              <div class="output-preview">
                <span><UiIcon name="check" :size="14" />清晰的任务目标</span
                ><span><UiIcon name="check" :size="14" />完整的约束条件</span
                ><span><UiIcon name="check" :size="14" />明确的输出格式</span>
              </div>
            </div>
            <div v-else class="result-body">
              <div class="result-badges">
                <span class="badge">{{
                  result.mode === "MOCK"
                    ? "模拟模式 · 模板演示"
                    : `模型接口 · ${result.model}`
                }}</span><span
                  class="score"
                  title="文本特征参考分，不代表模型效果"
                  >参考分 {{ result.qualityScore }}/100</span
                >
              </div>
              <p v-if="changed" class="message warning">
                输入已更改。下方仍为上次结果，重新优化后更新。
              </p>
              <p v-if="result.degraded" class="message warning">
                模型格式异常，结果已降级展示。
              </p>
              <pre>{{ result.optimizedPrompt }}</pre>
              <div class="result-actions">
                <button
                  class="secondary"
                  :disabled="loading"
                  @click="copyPrompt"
                >
                  <UiIcon :name="copied ? 'check' : 'copy'" :size="16" />{{
                    copied ? "已复制" : "复制提示词"
                  }}</button
                ><button
                  class="text-button"
                  :disabled="loading"
                  @click="exportResult"
                >
                  <UiIcon name="download" :size="16" />导出 Markdown
                </button>
              </div>
              <details class="source-details">
                <summary>
                  查看本次原始输入 · {{ sceneLabel(source.scene) }} /
                  {{ styleLabel(source.style) }}
                </summary>
                <p>{{ source.originalPrompt }}</p>
              </details>
              <div class="suggestions">
                <h3><UiIcon name="spark" :size="16" />改进建议</h3>
                <ol>
                  <li
                    v-for="suggestion in result.suggestions"
                    :key="suggestion"
                  >
                    {{ suggestion }}
                  </li>
                </ol>
              </div>
              <div class="feedback-row">
                <span
                  >这次结果有帮助吗？<small>{{
                    result.rating
                      ? `已评价 ${result.rating} 分`
                      : "1 分不理想 · 5 分很有帮助"
                  }}</small></span
                >
                <div class="rating-buttons">
                  <button
                    v-for="rating in 5"
                    :key="rating"
                    class="rating-button"
                    :class="{ selected: result.rating === rating }"
                    :disabled="busy"
                    :aria-pressed="result.rating === rating"
                    :aria-label="`${rating} 分`"
                    @click="rateResult(rating)"
                  >
                    {{ rating }}
                  </button>
                </div>
              </div>
              <p v-if="resultError" class="message error" role="alert">
                {{ resultError }}
              </p>
              <p class="disclaimer">
                参考分仅根据目标、约束和输出格式等文本特征计算，不代表真实模型效果。
              </p>
            </div>
          </section>
        </div>
        <footer class="workspace-footer">
          <span>少一点模糊，多一点明确。</span
          ><span>Prompt Studio · 单机版</span>
        </footer>
      </div>
    </main>
  </div>
  <div v-if="notice" class="toast" role="status">
    <UiIcon name="check" :size="16" />{{ notice }}
  </div>
  <dialog
    ref="dialog"
    class="confirm-dialog"
    aria-labelledby="confirm-title"
    aria-describedby="confirm-description"
    @cancel.prevent="cancelConfirmation"
  >
    <div v-if="confirmation">
      <h2 id="confirm-title">{{ confirmation.title }}</h2>
      <p id="confirm-description">{{ confirmation.description }}</p>
      <div class="dialog-actions">
        <button class="secondary" autofocus @click="cancelConfirmation">
          取消</button
        ><button class="primary" @click="confirmAction">
          {{ confirmation.label }}
        </button>
      </div>
    </div>
  </dialog>
</template>
