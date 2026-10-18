<script setup lang="ts">
/** 课程列表: GraphQL courses(tenantId, subject, page, size) → 后端 GET /courses */
import { ref, onMounted } from 'vue'
import { gql } from '../api'

interface Course { name?: string; subject?: string; gradeLevel?: string; description?: string }
const loading = ref(true)
const failed = ref(false)
const courses = ref<Course[]>([])

const QUERY = `
query($tenantId: String, $subject: String, $page: Int, $size: Int) {
  courses(tenantId: $tenantId, subject: $subject, page: $page, size: $size) {
    name subject gradeLevel description
  }
}`

onMounted(async () => {
  try {
    const d = await gql<{ courses: Course[] }>(QUERY, { tenantId: 'public', subject: null, page: 0, size: 20 })
    courses.value = Array.isArray(d?.courses) ? d.courses : []
  } catch { failed.value = true } // 后端不可达保持骨架
  loading.value = false
})
</script>

<template>
  <a-card title="课程学习">
    <a-spin v-if="loading" />
    <a-empty v-else-if="failed" description="课程服务暂不可达, 稍后重试" />
    <a-empty v-else-if="!courses.length" description="暂无课程数据" />
    <a-collapse v-else>
      <a-collapse-panel v-for="(c, i) in courses" :key="i" :header="`${c?.name ?? '—'} · ${c?.subject ?? '—'} · ${c?.gradeLevel ?? '—'}`">
        <div class="text-secondary">{{ c?.description ?? '—' }}</div>
      </a-collapse-panel>
    </a-collapse>
  </a-card>
</template>
