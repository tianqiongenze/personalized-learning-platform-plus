<script setup lang="ts">
import AiAssistant from '../../components/AiAssistant.vue'
const user = { name: '张同学', major: '工业互联网', credits: 42.5, creditsRequired: 150 }
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="title">{{ user.name }} · {{ user.major }}</view>
      <view>学分进度: {{ user.credits }} / {{ user.creditsRequired }}(岗课赛证融通)</view>
    </view>
    <!-- 每页 AI 助手 -->
    <AiAssistant page-context="个人中心" />
  </view>
</template>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; }
.title { font-size: 30rpx; font-weight: 600; margin-bottom: 12rpx; }
</style>
