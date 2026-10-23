<script setup lang="ts">
/** 资源域: 按资源ID单查 generatedResource(id), 返回 JSON 展示 */
import { ref } from 'vue'
import { gql } from '../api'

const resId = ref('')
const loading = ref(false)
const error = ref('')
const resultJson = ref('')

async function fetchResource() {
  if (!resId.value.trim()) { error.value = '请输入资源ID'; return }
  loading.value = true
  error.value = ''
  resultJson.value = ''
  try {
    const d = await gql<{ generatedResource: unknown }>(
      'query($id: ID!) { generatedResource(id: $id) }',
      { id: resId.value.trim() },
    )
    resultJson.value = JSON.stringify(d?.generatedResource ?? null, null, 2)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '资源服务暂不可达'
  }
  loading.value = false
}
</script>

<template>
  <a-card title="资源中心 · 6 类 AI 生成资源">
    <div class="text-secondary" style="margin-bottom:16px">
      资源域列表接口尚未开放, 当前支持按资源 ID 单查生成资源(错题变式/讲解/微课等)。
    </div>
    <a-space style="margin-bottom:16px">
      <a-input v-model:value="resId" style="width:320px" placeholder="输入资源ID, 如 res_001" @press-enter="fetchResource" />
      <a-button type="primary" :loading="loading" @click="fetchResource">查询资源</a-button>
    </a-space>
    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom:12px" />
    <pre v-if="resultJson" class="text-secondary" style="background:#fafafa; padding:12px; border-radius:6px; overflow:auto">{{ resultJson }}</pre>
  </a-card>
</template>
