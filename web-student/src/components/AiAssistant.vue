<script setup lang="ts">
/**
 * 每页 AI 助手(Web 端) — AG-UI 标准事件流(docs/MULTI_CLIENT_DESIGN_SPEC.md §3.2)
 * 事件消费: RUN_STARTED → TEXT_MESSAGE_CONTENT(delta 累积) → RUN_FINISHED
 */
import { ref, nextTick } from 'vue'
import { aguiRun } from '../api'

const props = defineProps<{ pageContext: string }>()
const open = ref(false)
const input = ref('')
const busy = ref(false)
const messages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])
const listRef = ref<HTMLElement>()

async function send() {
  const q = input.value.trim()
  if (!q || busy.value) return
  input.value = ''
  open.value = true
  busy.value = true
  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', content: '' })
  await nextTick()
  try {
    await aguiRun(q, ev => {
      const last = messages.value[messages.value.length - 1]
      if (ev.type === 'TEXT_MESSAGE_CONTENT') last.content += ev.delta ?? ''
      if (ev.type === 'RUN_FINISHED') busy.value = false
    })
  } catch {
    const last = messages.value[messages.value.length - 1]
    last.content += '（网络异常, 请稍后重试）'
  }
  busy.value = false
  listRef.value?.scrollTo({ top: listRef.value.scrollHeight })
}
</script>

<template>
  <div class="fab" :title="'AI 学习助手'" @click="open = !open">
    <span v-if="!open">AI</span><span v-else>×</span>
  </div>
  <div v-if="open" class="panel">
    <div class="panel-title">AI 学习助手 · {{ pageContext }}</div>
    <div ref="listRef" class="panel-body">
      <div v-if="!messages.length" class="hint">问我任何学习问题, 例如: "PLC 梯形图怎么入门?"</div>
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">{{ m.content }}</div>
      <div v-if="busy" class="typing">●●●</div>
    </div>
    <div class="panel-input">
      <a-input v-model:value="input" placeholder="输入问题…" @press-enter="send" :disabled="busy" />
      <a-button type="primary" :loading="busy" @click="send">发送</a-button>
    </div>
  </div>
</template>

<style scoped>
.fab { position: fixed; right: var(--assistant-fab-offset, 32px); bottom: 32px; width: 56px; height: 56px;
  border-radius: 50%; background: var(--brand-primary); color: #fff; display: flex; align-items: center;
  justify-content: center; font-weight: 700; cursor: pointer; box-shadow: 0 4px 16px rgba(22,119,255,.4); z-index: 100; }
.panel { position: fixed; right: 32px; bottom: 100px; width: 360px; height: 480px; background: var(--neutrals-card);
  border-radius: var(--radius-lg, 16px); box-shadow: 0 8px 32px rgba(0,0,0,.15); display: flex; flex-direction: column; z-index: 100; }
.panel-title { padding: 12px 16px; border-bottom: 1px solid #eee; font-weight: 600; }
.panel-body { flex: 1; overflow-y: auto; padding: 12px 16px; }
.msg { margin-bottom: 8px; padding: 8px 12px; border-radius: 12px; max-width: 85%; white-space: pre-wrap; }
.msg.user { background: var(--brand-primary); color: #fff; margin-left: auto; }
.msg.assistant { background: var(--neutrals-bg); }
.hint { color: var(--neutrals-textSecondary); text-align: center; margin-top: 40px; }
.typing { color: var(--neutrals-textSecondary); }
.panel-input { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eee; }
</style>
