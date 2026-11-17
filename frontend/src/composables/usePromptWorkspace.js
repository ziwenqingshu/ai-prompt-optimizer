import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from "vue";

const DRAFT_KEY = "prompt-optimizer.draft.v1";
export const scenes = [
  {
    value: "GENERAL",
    label: "通用问答",
    icon: "message",
    example:
      "为一名每周有 5 小时空闲的初学者，制定一个为期 4 周的 Python 学习计划。请按周列出目标、练习和验收方式。",
  },
  {
    value: "SUMMARY",
    label: "文本总结",
    icon: "file",
    example:
      "请帮我总结一篇长文章，提炼核心观点、关键论据和行动建议。保留原文的重要数字，以分点列表呈现，不添加原文没有的信息。",
  },
  {
    value: "CODE",
    label: "代码助手",
    icon: "code",
    example:
      "用 Python 编写一个读取 CSV 并统计每列缺失值的函数。说明输入输出，处理文件不存在和空文件的情况，并提供可运行的测试示例。",
  },
  {
    value: "RESUME",
    label: "简历优化",
    icon: "briefcase",
    example:
      "帮我优化 Java 后端实习岗位的项目经历。突出我的实际贡献、技术选择和可验证的成果；不要编造数据，缺失信息用待补充标记。",
  },
];
export const styles = [
  { value: "CONCISE", label: "简洁" },
  { value: "PROFESSIONAL", label: "专业" },
  { value: "DETAILED", label: "详细" },
];
export const sceneLabel = (value) =>
  scenes.find((scene) => scene.value === value)?.label || value;
export const styleLabel = (value) =>
  styles.find((style) => style.value === value)?.label || value;
const emptyForm = () => ({
  originalPrompt: "",
  scene: "GENERAL",
  style: "PROFESSIONAL",
});
const sameForm = (a, b) =>
  b &&
  a.originalPrompt.trim() === b.originalPrompt.trim() &&
  a.scene === b.scene &&
  a.style === b.style;

export function usePromptWorkspace() {
  const form = reactive(emptyForm());
  const result = ref(null);
  const source = ref(null);
  const history = ref([]);
  const query = ref("");
  const sceneFilter = ref("");
  const error = ref("");
  const historyError = ref("");
  const resultError = ref("");
  const loading = ref(false);
  const historyLoading = ref(false);
  const feedbackLoading = ref(false);
  const deleting = ref(false);
  const copied = ref(false);
  const notice = ref("");
  const draftState = ref("草稿仅保存在此浏览器");
  const modelStatus = ref(null);
  const connectionMessage = ref("");
  const connectionError = ref(false);
  const testingConnection = ref(false);
  const dialog = ref(null);
  const confirmation = ref(null);
  const editor = ref(null);
  const resultPanel = ref(null);
  let noticeTimer;
  const busy = computed(
    () =>
      loading.value ||
      feedbackLoading.value ||
      testingConnection.value ||
      deleting.value,
  );
  // Match textarea maxlength and Java @Size (UTF-16 code units).
  const count = computed(() => form.originalPrompt.length);
  const changed = computed(
    () => !!result.value && !sameForm(form, source.value),
  );
  const filteredHistory = computed(() => {
    const term = query.value.trim().toLocaleLowerCase();
    return history.value.filter(
      (item) =>
        (!sceneFilter.value || item.scene === sceneFilter.value) &&
        (!term ||
          `${item.originalPrompt}\n${item.optimizedPrompt}`
            .toLocaleLowerCase()
            .includes(term)),
    );
  });
  const activeExample = computed(
    () => scenes.find((scene) => scene.value === form.scene) || scenes[0],
  );

  function notify(message) {
    notice.value = message;
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => {
      notice.value = "";
    }, 4000);
  }
  async function api(url, options) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.error || "请求失败，请稍后重试。");
    return body;
  }
  const jsonRequest = (method, body) => ({
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  async function loadHistory() {
    historyLoading.value = true;
    historyError.value = "";
    try {
      history.value = await api("/api/prompts/history");
    } catch {
      historyError.value = "历史记录加载失败，请重试。";
    } finally {
      historyLoading.value = false;
    }
  }
  async function loadModelStatus() {
    connectionError.value = false;
    connectionMessage.value = "";
    try {
      modelStatus.value = await api("/api/model/status");
    } catch {
      connectionError.value = true;
      connectionMessage.value = "无法连接本机服务，请确认后端已启动。";
    }
  }
  async function testConnection() {
    if (busy.value) return;
    testingConnection.value = true;
    connectionMessage.value = "";
    connectionError.value = false;
    try {
      const body = await api("/api/model/test", jsonRequest("POST", {}));
      connectionMessage.value = body.available
        ? `连接成功 · ${body.model} · ${body.latencyMs} ms${body.degraded ? "（响应格式需要兼容处理）" : ""}`
        : "当前为模拟模式，未调用真实模型。";
    } catch (exception) {
      connectionError.value = true;
      connectionMessage.value = exception.message;
    } finally {
      testingConnection.value = false;
    }
  }

  function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? ""
      : new Intl.DateTimeFormat("zh-CN", {
          month: "numeric",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        }).format(date);
  }
  function clearResult() {
    result.value = null;
    source.value = null;
    resultError.value = "";
    copied.value = false;
    error.value = "";
  }
  function ask(title, description, label, action) {
    confirmation.value = { title, description, label, action };
    dialog.value.showModal();
  }
  function cancelConfirmation() {
    dialog.value.close();
    confirmation.value = null;
  }
  async function confirmAction() {
    const action = confirmation.value?.action;
    cancelConfirmation();
    await action?.();
  }
  function focusEditor() {
    nextTick(() => editor.value?.focus());
  }
  function newPrompt() {
    if (busy.value) return;
    const reset = () => {
      Object.assign(form, emptyForm());
      clearResult();
      focusEditor();
    };
    if (form.originalPrompt.trim() && !sameForm(form, source.value)) {
      ask(
        "新建提示词？",
        "当前未完成的草稿会被清空，已保存的历史记录不受影响。",
        "新建",
        reset,
      );
    } else reset();
  }
  function useExample() {
    if (busy.value) return;
    const apply = () => {
      form.originalPrompt = activeExample.value.example;
      clearResult();
      focusEditor();
      notify("已填入示例，可以按需修改");
    };
    if (
      form.originalPrompt.trim() &&
      form.originalPrompt !== activeExample.value.example
    ) {
      ask(
        "使用示例替换草稿？",
        "当前输入会被示例替换，已保存的历史记录不受影响。",
        "使用示例",
        apply,
      );
    } else apply();
  }
  async function revealResult() {
    await nextTick();
    resultPanel.value?.focus({ preventScroll: true });
    if (window.matchMedia("(max-width: 1100px)").matches)
      resultPanel.value?.scrollIntoView({
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
        block: "start",
      });
  }
  async function optimize() {
    if (busy.value) return;
    error.value = "";
    if (!form.originalPrompt.trim()) {
      error.value = "请先输入原始提示词。";
      focusEditor();
      return;
    }
    if (count.value > 2000) {
      error.value = "原始提示词不能超过 2000 个字符。";
      return;
    }
    const request = { ...form, originalPrompt: form.originalPrompt.trim() };
    loading.value = true;
    try {
      const body = await api(
        "/api/prompts/optimize",
        jsonRequest("POST", request),
      );
      result.value = body;
      source.value = request;
      copied.value = false;
      resultError.value = "";
      history.value = [
        { ...body, ...request, id: body.historyId },
        ...history.value.filter((item) => item.id !== body.historyId),
      ].slice(0, 100);
      notify("优化完成，已保存到历史记录");
      await revealResult();
    } catch (exception) {
      error.value = exception.message || "网络异常，请重试。";
    } finally {
      loading.value = false;
    }
  }
  function selectHistory(item) {
    if (busy.value) return;
    const restore = () => {
      Object.assign(form, {
        originalPrompt: item.originalPrompt,
        scene: item.scene,
        style: item.style,
      });
      source.value = { ...form };
      result.value = { ...item, historyId: item.id };
      error.value = "";
      resultError.value = "";
      copied.value = false;
      revealResult();
    };
    if (
      form.originalPrompt.trim() &&
      !sameForm(form, item) &&
      !sameForm(form, source.value)
    ) {
      ask(
        "打开这条历史记录？",
        "当前未完成的草稿会被替换，历史记录不会改变。",
        "打开记录",
        restore,
      );
    } else restore();
  }
  function requestDelete(item) {
    if (busy.value) return;
    ask(
      "删除这条记录？",
      "删除后无法恢复。当前输入的文字会保留。",
      "确认删除",
      () => removeHistory(item.id),
    );
  }
  async function removeHistory(id) {
    deleting.value = true;
    historyError.value = "";
    try {
      const response = await fetch(
        `/api/prompts/history/${encodeURIComponent(id)}`,
        { method: "DELETE" },
      );
      if (!response.ok && response.status !== 404)
        throw new Error("删除失败，请重试。");
      history.value = history.value.filter((item) => item.id !== id);
      if (result.value?.historyId === id) clearResult();
      notify("历史记录已删除");
    } catch (exception) {
      historyError.value = exception.message;
    } finally {
      deleting.value = false;
    }
  }
  async function rateResult(rating) {
    if (!result.value?.historyId || busy.value) return;
    const id = result.value.historyId;
    feedbackLoading.value = true;
    resultError.value = "";
    try {
      const body = await api(
        `/api/prompts/history/${encodeURIComponent(id)}/feedback`,
        jsonRequest("PUT", { rating }),
      );
      if (result.value?.historyId === id) result.value.rating = body.rating;
      history.value = history.value.map((item) =>
        item.id === id ? { ...item, rating: body.rating } : item,
      );
      notify(`已保存 ${rating} 分评价`);
    } catch (exception) {
      resultError.value = exception.message;
    } finally {
      feedbackLoading.value = false;
    }
  }
  async function copyPrompt() {
    if (!result.value) return;
    resultError.value = "";
    const text = result.value.optimizedPrompt;
    const id = result.value.historyId;
    try {
      try {
        await navigator.clipboard.writeText(text);
      } catch {
        const previous = document.activeElement;
        const input = document.createElement("textarea");
        input.value = text;
        input.style.position = "fixed";
        input.style.opacity = "0";
        document.body.appendChild(input);
        input.select();
        const succeeded = document.execCommand("copy");
        input.remove();
        previous?.focus();
        if (!succeeded) throw new Error("copy failed");
      }
      if (result.value?.historyId === id) copied.value = true;
      notify("提示词已复制");
    } catch {
      resultError.value = "复制失败，请手动选择文本复制。";
    }
  }
  function exportResult() {
    if (!result.value || !source.value) return;
    const value = result.value;
    const markdown = `# 提示词优化记录\n\n- 场景：${sceneLabel(source.value.scene)}\n- 风格：${styleLabel(source.value.style)}\n- 模式：${value.mode === "MOCK" ? "模拟模式（模板演示）" : value.model}\n\n## 原始提示词\n\n${source.value.originalPrompt}\n\n## 优化后的提示词\n\n${value.optimizedPrompt}\n\n## 改进建议\n\n${value.suggestions.map((item) => `- ${item}`).join("\n")}\n`;
    const url = URL.createObjectURL(
      new Blob([markdown], { type: "text/markdown;charset=utf-8" }),
    );
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `提示词-${new Date().toISOString().slice(0, 10)}.md`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notify("已导出 Markdown 文件");
  }
  function shortcut(event) {
    if (
      (event.ctrlKey || event.metaKey) &&
      event.key === "Enter" &&
      !event.isComposing &&
      !dialog.value?.open
    ) {
      event.preventDefault();
      if (modelStatus.value?.configured !== false) optimize();
    }
  }
  onMounted(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(DRAFT_KEY) || "null");
      if (
        saved &&
        typeof saved.originalPrompt === "string" &&
        scenes.some((item) => item.value === saved.scene) &&
        styles.some((item) => item.value === saved.style)
      ) {
        Object.assign(form, {
          originalPrompt: saved.originalPrompt.slice(0, 2000),
          scene: saved.scene,
          style: saved.style,
        });
        if (form.originalPrompt) draftState.value = "已恢复上次草稿";
      }
    } catch {
      draftState.value = "草稿暂不可用；仍可正常优化";
    }
    watch(
      form,
      () => {
        try {
          localStorage.setItem(DRAFT_KEY, JSON.stringify(form));
          draftState.value = form.originalPrompt
            ? "草稿已自动保存"
            : "草稿仅保存在此浏览器";
        } catch {
          draftState.value = "草稿未保存，请保留当前页面";
        }
      },
      { deep: true, flush: "sync" },
    );
    loadHistory();
    loadModelStatus();
    window.addEventListener("keydown", shortcut);
  });
  onUnmounted(() => {
    clearTimeout(noticeTimer);
    window.removeEventListener("keydown", shortcut);
  });
  return {
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
  };
}
