<script setup lang="ts">
/** 学习路径: learningPaths(courseId) + assignments(courseId)(TeachingResolver 契约) */
import { ref, onMounted } from 'vue'
import { gql } from '../api'

interface Step { title?: string; status?: string }
interface Assignment { title?: string; dueAt?: string }

const courseId = ref('math_101')
const loading = ref(true)
const failed = ref(false)
const steps = ref<Step[]>([])
const assignments = ref<Assignment[]>([])

const QUERY = `
query($courseId: ID) {
  learningPaths(courseId: $courseId) { title status }
  assignments(courseId: $courseId) { title dueAt }
}`

async function load() {
  loading.value = true
  failed.value = false
  try {
    const d = await gql<{ learningPaths: Step[]; assignments: Assignment[] }>(QUERY, { courseId: courseId.value })
    steps.value = Array.isArray(d?.learningPaths) ? d.learningPaths : []
    assignments.value = Array.isArray(d?.assignments) ? d.assignments : []
  } catch { failed.value = true }
  loading.value = false
}

onMounted(load)

const columns = [
  { title: '作业', dataIndex: 'title', key: 'title' },
  { title: '截止时间', dataIndex: 'dueAt', key: 'dueAt' },
]
</script>

<template>
  <a-card title="学习路径 · ZPD 桥接">
    <a-space style="margin-bottom:16px">
      <a-input v-model:value="courseId" style="width:260px" placeholder="课程ID" />
      <a-button type="primary" @click="load">查询</a-button>
    </a-space>
    <a-spin v-if="loading" />
    <template v-else>
      <a-empty v-if="failed" description="教学服务暂不可达" />
      <template v-else>
        <a-timeline v-if="steps.length" style="margin:8px 0 24px">
          <a-timeline-item v-for="(s, i) in steps" :key="i">
            <b>{{ s?.title ?? '—' }}</b>
            <span class="text-secondary" style="margin-left:8px">{{ s?.status ?? '—' }}</span>
          </a-timeline-item>
        </a-timeline>
        <a-empty v-else description="该课程暂无学习路径" />
        <h4>课程作业</h4>
        <a-table v-if="assignments.length" :columns="columns" :data-source="assignments" :pagination="false" size="small" row-key="title">
          <template #bodyCell="{ column, record }">
            <span v-if="column.key === 'title'">{{ record?.title ?? '—' }}</span>
            <span v-else-if="column.key === 'dueAt'">{{ record?.dueAt ?? '—' }}</span>
          </template>
        </a-table>
        <a-empty v-else description="暂无作业" />
      </template>
    </template>
  </a-card>
</template>
