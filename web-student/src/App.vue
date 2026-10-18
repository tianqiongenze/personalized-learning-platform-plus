<script setup lang="ts">
import { useRouter } from 'vue-router'
import AiAssistant from './components/AiAssistant.vue'

const router = useRouter()
const navs = [
  { key: '/', label: '学情首页' },
  { key: '/courses', label: '课程学习' },
  { key: '/path', label: '学习路径' },
  { key: '/review', label: '复习卡片' },
  { key: '/resources', label: '资源中心' },
  { key: '/profile', label: '我的画像' },
]
</script>

<template>
  <a-layout style="min-height: 100vh">
    <a-layout-header class="header">
      <span class="brand" @click="router.push('/')">千人千面 · 个性化学习平台</span>
      <a-menu theme="dark" mode="horizontal" selected-keys="[]" style="flex:1; background:transparent">
        <a-menu-item v-for="n in navs" :key="n.key" @click="router.push(n.key)">{{ n.label }}</a-menu-item>
      </a-menu>
    </a-layout-header>
    <a-layout-content class="content">
      <router-view />
    </a-layout-content>
    <!-- 每页 AI 助手(AG-UI 标准事件流驱动) -->
    <AiAssistant page-context="global" />
  </a-layout>
</template>

<style>
.header { display: flex; align-items: center; gap: 24px; background: var(--brand-primary); }
.brand { color: #fff; font-size: 17px; font-weight: 600; cursor: pointer; white-space: nowrap; }
.content { padding: 24px; background: var(--neutrals-bg); }
</style>
