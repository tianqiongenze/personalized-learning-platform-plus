<script setup lang="ts">
/** 我的画像: memoryProfile(studentId) + masteryProfile(studentId, skillIds) */
import { ref, onMounted } from 'vue'
import { gql } from '../api'

const STUDENT = 'e2e-stu-1'
const loading = ref(true)
const failed = ref(false)
const memory = ref<Record<string, unknown> | null>(null)
const skills = ref<{ skill?: string; mastery?: number }[]>([])

const QUERY = `
query($studentId: ID, $skillIds: [String]) {
  memoryProfile(studentId: $studentId) { version profile }
  masteryProfile(studentId: $studentId, skillIds: $skillIds) { skill mastery }
}`

onMounted(async () => {
  try {
    const d = await gql<{ memoryProfile: Record<string, unknown>; masteryProfile: { skill?: string; mastery?: number }[] }>(
      QUERY, { studentId: STUDENT, skillIds: [] },
    )
    memory.value = d?.memoryProfile ?? null
    skills.value = Array.isArray(d?.masteryProfile) ? d.masteryProfile : []
  } catch { failed.value = true }
  loading.value = false
})
</script>

<template>
  <a-card title="我的画像 · 记忆金字塔 L3">
    <a-spin v-if="loading" />
    <a-empty v-else-if="failed" description="画像服务暂不可达" />
    <template v-else>
      <h4>L3 记忆画像摘要</h4>
      <div class="card-mastery" style="padding:8px 12px; margin-bottom:16px">
        <div>版本: {{ (memory as any)?.version ?? '—' }}</div>
        <div>画像: <span class="text-secondary">{{ JSON.stringify((memory as any)?.profile ?? '—') }}</span></div>
      </div>
      <h4>掌握度</h4>
      <template v-if="skills.length">
        <div v-for="(s, i) in skills" :key="i" style="margin-bottom:10px">
          <div>{{ s?.skill ?? '—' }}</div>
          <a-progress :percent="Math.round((Number(s?.mastery) || 0) * 100)" size="small" />
        </div>
      </template>
      <a-empty v-else description="暂无掌握度数据" />
    </template>
  </a-card>
</template>
