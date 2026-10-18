<script setup lang="ts">
/** 复习卡片: reviewPlan(studentId) → plan 列表(kp/next 时间) */
import { ref, onMounted } from 'vue'
import { gql } from '../api'

const STUDENT = 'e2e-stu-1'
const loading = ref(true)
const failed = ref(false)
const plans = ref<{ kp?: string; next?: string }[]>([])

const QUERY = `
query($studentId: ID) {
  reviewPlan(studentId: $studentId) { kp next }
}`

onMounted(async () => {
  try {
    const d = await gql<{ reviewPlan: { kp?: string; next?: string }[] }>(QUERY, { studentId: STUDENT })
    plans.value = Array.isArray(d?.reviewPlan) ? d.reviewPlan : []
  } catch { failed.value = true }
  loading.value = false
})
</script>

<template>
  <a-card title="复习卡片 · SM-2">
    <a-spin v-if="loading" />
    <a-empty v-else-if="failed" description="复习服务暂不可达" />
    <a-empty v-else-if="!plans.length" description="今日无到期卡片" />
    <a-card v-else v-for="(p, i) in plans" :key="i" size="small" style="margin-bottom:12px">
      <div><b>知识点: {{ p?.kp ?? '—' }}</b></div>
      <div class="text-secondary">下次复习: {{ p?.next ?? '—' }}</div>
    </a-card>
  </a-card>
</template>
