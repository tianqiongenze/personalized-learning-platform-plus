<script setup lang="ts">
/** 复习卡片页(APP/小程序) — SM-2 到期卡片 + 掌握/再学上报(POST /kt/events) */
import { ref } from 'vue'
import AiAssistant from '../../components/AiAssistant.vue'

const cards = ref([
  { id: 1, kp: '动力电池管理', front: 'BMS 三级架构是什么?', back: '采集级→主控级→整车级, 负责电压/温度采集与均衡', next: '今天' },
  { id: 2, kp: 'PLC 梯形图', front: '常开触点与常闭触点的区别?', back: '常开=线圈得电闭合; 常闭反之', next: '今天' },
  { id: 3, kp: '伺服控制', front: 'PID 三参数各调节什么?', back: 'P 快速性, I 消除稳态误差, D 抑制超调', next: '今天' },
])
const current = ref(0)
const flipped = ref(false)

/** 上报学习事件到 kt-service(BKT 更新掌握度)。失败静默: 学习不因上报失败中断 */
function reportEvent(correct: boolean) {
  uni.request({
    url: `${import.meta.env.VITE_KT_URL || 'http://localhost:8086'}/kt/events`,
    method: 'POST',
    data: { studentId: 'guest', skillId: cards.value[current.value].id, correct },
    timeout: 3000,
    fail: () => { /* 上报失败不打断学习主链 */ },
  })
}

function grade(good: boolean) {
  reportEvent(good)
  flipped.value = false
  current.value = (current.value + 1) % cards.value.length
  uni.showToast({ title: good ? '已按 SM-2 排期' : '将更频繁复习', icon: 'none' })
}
</script>

<template>
  <view class="page">
    <view v-if="cards.length" class="card" @click="flipped = !flipped">
      <view class="kp">{{ cards[current].kp }}</view>
      <view class="content">{{ flipped ? cards[current].back : cards[current].front }}</view>
      <view class="hint">{{ flipped ? '点击返回正面' : '点击翻面' }}</view>
    </view>
    <view v-if="flipped" class="actions">
      <button class="btn bad" @click="grade(false)">再学一次</button>
      <button class="btn good" @click="grade(true)">已掌握</button>
    </view>
    <view class="progress">{{ current + 1 }} / {{ cards.length }}</view>
    <AiAssistant page-context="复习卡片" />
  </view>
</template>

<style scoped lang="scss">
@import '../../styles/tokens.scss';
.page { padding: $xs; }
.card { min-height: 400rpx; background: #fff; border-radius: $lg; padding: $xl; display: flex;
  flex-direction: column; align-items: center; justify-content: center; }
.kp { color: var(--brand-primary, #1677FF); font-size: $sm + 8rpx; font-weight: 600; }
.content { font-size: $lg + 8rpx; margin-top: $md; text-align: center; }
.hint { color: #999; font-size: $sm; margin-top: $lg; }
.actions { display: flex; gap: $md; margin-top: $lg; }
.btn { flex: 1; border-radius: $lg; color: #fff; }
.bad { background: var(--semantics-error, #FF4D4F); }
.good { background: var(--semantics-success, #52C41A); }
.progress { text-align: center; color: #999; margin-top: $md; }
</style>
