<script setup>
import { computed, reactive, ref } from 'vue'

const form = reactive({
  originalPrompt: '',
  scene: 'GENERAL',
  style: 'PROFESSIONAL'
})
const result = ref(null)
const error = ref('')
const loading = ref(false)
const copied = ref(false)
const count = computed(() => Array.from(form.originalPrompt).length)

async function optimize() {
  error.value = ''
  result.value = null
  copied.value = false
  if (!form.originalPrompt.trim()) {
    error.value = '请先输入原始提示词。'
    return
  }
  if (count.value > 2000) {
    error.value = '原始提示词不能超过 2000 个字符。'
    return
  }

  loading.value = true
  try {
    const response = await fetch('/api/prompts/optimize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form)
    })
    const body = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(body.error || '服务异常，请稍后重试。')
    }
    result.value = body
  } catch (exception) {
    error.value = exception.message || '网络异常，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function copyPrompt() {
  try {
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(result.value.optimizedPrompt)
        copied.value = true
        return
      } catch {
        // HTTP-by-IP deployments may not grant the Clipboard API permission.
      }
    }
    const input = document.createElement('textarea')
    input.value = result.value.optimizedPrompt
    document.body.appendChild(input)
    input.select()
    const succeeded = document.execCommand('copy')
    input.remove()
    if (!succeeded) throw new Error('copy failed')
    copied.value = true
  } catch {
    error.value = '复制失败，请手动选择文本复制。'
  }
}
</script>

<template>
  <main class="page-shell">
    <section class="hero">
      <p class="eyebrow">AI PROMPT OPTIMIZER</p>
      <h1>智能提示词优化助手</h1>
      <p>把模糊想法整理成更清晰、更可执行的提示词。</p>
    </section>

    <section class="card" aria-label="提示词优化表单">
      <label for="original-prompt">原始提示词</label>
      <textarea
        id="original-prompt"
        v-model="form.originalPrompt"
        maxlength="2000"
        rows="9"
        placeholder="例如：帮我优化一份 Java 后端实习简历"
      />
      <p class="counter">{{ count }} / 2000</p>

      <div class="selectors">
        <label>
          使用场景
          <select v-model="form.scene">
            <option value="SUMMARY">文本总结</option>
            <option value="CODE">代码助手</option>
            <option value="RESUME">简历优化</option>
            <option value="GENERAL">通用问答</option>
          </select>
        </label>
        <label>
          输出风格
          <select v-model="form.style">
            <option value="CONCISE">简洁</option>
            <option value="PROFESSIONAL">专业</option>
            <option value="DETAILED">详细</option>
          </select>
        </label>
      </div>

      <button class="primary" type="button" :disabled="loading" @click="optimize">
        {{ loading ? '正在优化…' : '开始优化' }}
      </button>
      <p v-if="error" class="message error" role="alert">{{ error }}</p>
    </section>

    <section v-if="result" class="card result-card" aria-live="polite">
      <div class="result-heading">
        <div>
          <p class="eyebrow">OPTIMIZED RESULT</p>
          <h2>优化后的提示词</h2>
        </div>
        <span v-if="result.mode === 'MOCK'" class="badge">模拟模式</span>
      </div>
      <p v-if="result.degraded" class="message warning">模型格式异常，结果已降级展示。</p>
      <pre>{{ result.optimizedPrompt }}</pre>
      <button class="secondary" type="button" @click="copyPrompt">
        {{ copied ? '已复制' : '复制提示词' }}
      </button>

      <h3>改进建议</h3>
      <ol>
        <li v-for="suggestion in result.suggestions" :key="suggestion">{{ suggestion }}</li>
      </ol>
    </section>
  </main>
</template>
