<script setup lang="ts">
import AiAssistant from '../../components/AiAssistant.vue'
const steps = [
  { seq: 1, title: 'PLC 基础指令', status: '已完成' },
  { seq: 2, title: '传感器与信号采集', status: '学习中' },
  { seq: 3, title: '产线数据采集与监控(AI 生成实训任务)', status: '待学习' },
]
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="title">我的个性化学习路径(AI 动态规划)</view>
      <view v-for="s in steps" :key="s.seq" class="step">
        <text class="seq">{{ s.seq }}</text>
        <text class="t">{{ s.title }}</text>
        <text class="s">{{ s.status }}</text>
      </view>
    </view>
    <!-- 每页 AI 助手 -->
    <AiAssistant page-context="学习路径" />
  </view>
</template>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; }
.title { font-size: 30rpx; font-weight: 600; margin-bottom: 20rpx; }
.step { display: flex; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.seq { color: #1677ff; font-weight: 600; }
.t { flex: 1; margin-left: 16rpx; }
.s { color: #999; font-size: 24rpx; }
</style>
