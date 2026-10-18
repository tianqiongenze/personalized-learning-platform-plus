<script setup lang="ts">
import AiAssistant from '../../components/AiAssistant.vue'
const courses = [
  { id: 'C001', name: '工业互联网技术', progress: 0.62 },
  { id: 'C002', name: '智能网联汽车概论', progress: 0.35 },
  { id: 'C003', name: '机电一体化系统设计', progress: 0.81 },
]
</script>

<template>
  <view class="page">
    <view v-for="c in courses" :key="c.id" class="card">
      <view class="title">{{ c.name }}</view>
      <progress :percent="Math.round(c.progress * 100)" stroke-width="6" />
    </view>
    <!-- 每页 AI 助手: 课程上下文 -->
    <AiAssistant page-context="课程学习" :extra="{ course: 'C001' }" />
  </view>
</template>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; margin-bottom: 12rpx; }
</style>
