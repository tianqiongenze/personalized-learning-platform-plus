<script setup lang="ts">
import AiAssistant from '../../components/AiAssistant.vue'
const recommendations = [
  { id: 1, title: '《机电一体化》第3章: 伺服电机控制', reason: '基于你的掌握度推荐' },
  { id: 2, title: '智能网联新能源汽车产线认知实训', reason: '"33618" 产业场景 · 岗位相关' },
  { id: 3, title: '《电气自动化》PLC 编程测验', reason: 'AI 生成的差异化练习题组' },
]
</script>

<template>
  <view class="page">
    <view class="banner">千人千面 · AI 即时生成 · 按需供给</view>
    <view v-for="r in recommendations" :key="r.id" class="card">
      <view class="title">{{ r.title }}</view>
      <view class="reason">{{ r.reason }}</view>
    </view>
    <!-- 每页 AI 助手 -->
    <AiAssistant page-context="首页推荐" />
  </view>
</template>

<style scoped>
.page { padding: 24rpx; }
.banner { background: linear-gradient(90deg,#1677ff,#69b1ff); color: #fff; border-radius: 16rpx; padding: 40rpx 32rpx; font-size: 34rpx; font-weight: 600; margin-bottom: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; }
.reason { color: #1677ff; font-size: 24rpx; margin-top: 8rpx; }
</style>
