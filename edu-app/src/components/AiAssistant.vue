<script setup lang="ts">
/**
 * 每页 AI 助手(三端通用组件) —— 跨端流式策略:
 * - H5/App(原生): EventSource(SSE) 直连 realtime-gateway
 * - 微信小程序: wx.connectSocket(WebSocket) 连同一网关 /ws 通道
 * 后端均为 agent-service 的 LangGraph ReAct 助手, 保证三端回答一致。
 * 每个页面传入自己的 pageContext, 助手据此加载页面上下文(如课程页携带 courseId)。
 */
import { ref } from 'vue'

const props = defineProps<{ pageContext: string; extra?: Record<string, string> }>()
const open = ref(false)
const input = ref('')
const reply = ref('')
const streaming = ref(false)
const messages = ref<{ role: string; content: string }[]>([])

const platform = (() => {
  // #ifdef MP-WEIXIN
  return 'mp-weixin'
  // #endif
  // #ifndef MP-WEIXIN
  return 'h5-app'
  // #endif
})()

function send() {
  if (!input.value.trim()) return
  open.value = true
  streaming.value = true
  messages.value.push({ role: 'user', content: input.value })
  const q = input.value
  input.value = ''
  messages.value.push({ role: 'assistant', content: '' })

  const onToken = (content: string) => {
    const last = messages.value[messages.value.length - 1]
    last.content += content
  }
  const onDone = () => { streaming.value = false }

  // #ifdef MP-WEIXIN
  const task = uni.connectSocket({
    url: `${import.meta.env.VITE_WS_URL}?studentId=guest`,
    complete: () => {},
  })
  task.onMessage(res => {
    const frame = JSON.parse(res.data as string)
    if (frame.type === 'token') onToken(JSON.parse(frame.payload).content ?? '')
    if (frame.type === 'done') onDone()
  })
  task.onOpen(() => task.send(JSON.stringify({ type: 'chat', message: q, pageContext: props.pageContext })))
  // #endif

  // #ifndef MP-WEIXIN
  const es = new EventSource(
    `${import.meta.env.VITE_API_URL}/sse?pageContext=${encodeURIComponent(props.pageContext)}&message=${encodeURIComponent(q)}`)
  es.onmessage = e => {
    if (e.data === '[DONE]') { es.close(); onDone(); return }
    try { onToken(JSON.parse(e.data).content ?? '') } catch { /* noop */ }
  }
  es.onerror = () => { es.close(); onDone() }
  // #endif
}
</script>

<template>
  <view>
    <button class="fab" @click="open = !open">AI</button>
    <view v-if="open" class="panel">
      <view class="panel-title">AI 助手 · {{ pageContext }}</view>
      <scroll-view scroll-y class="msgs">
        <view v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          {{ m.role === 'user' ? '我：' + m.content : 'AI：' + m.content }}
        </view>
        <view v-if="streaming" class="streaming">思考中…</view>
      </scroll-view>
      <view class="input-row">
        <input v-model="input" class="ipt" placeholder="问我任何学习问题" confirm-type="send" @confirm="send" />
        <button size="mini" type="primary" :disabled="streaming" @click="send">发送</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.fab { position: fixed; right: 24rpx; bottom: 140rpx; width: 96rpx; height: 96rpx; border-radius: 50%; background: #1677ff; color: #fff; z-index: 99; }
.panel { position: fixed; right: 24rpx; bottom: 250rpx; width: 620rpx; height: 700rpx; background: #fff; border-radius: 16rpx; box-shadow: 0 8rpx 32rpx rgba(0,0,0,.15); padding: 24rpx; z-index: 99; display: flex; flex-direction: column; }
.panel-title { font-weight: 600; margin-bottom: 12rpx; }
.msgs { flex: 1; }
.msg { margin-bottom: 12rpx; font-size: 26rpx; white-space: pre-wrap; }
.msg.assistant { color: #444; }
.streaming { color: #999; font-size: 24rpx; }
.input-row { display: flex; gap: 12rpx; align-items: center; }
.ipt { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 8rpx 16rpx; }
</style>
