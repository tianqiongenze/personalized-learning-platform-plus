<script setup lang="ts">
/** 学情首页: 掌握度环 + 世界模型 what-if 推荐(阶段冒烟数据, GraphQL 就绪后替换) */
import { ref, onMounted } from 'vue'
import { mastery, worldWhatIf } from '../api'

const overall = ref<number | null>(null)
const weak = ref<{ kp: number; mastery: number }[]>([])
const suggestion = ref<string>('')

onMounted(async () => {
  try {
    const m = await mastery('e2e-stu-1')
    overall.value = m?.overallMastery ?? null
    weak.value = Array.isArray(m?.weakKps) ? m.weakKps.slice(0, 3) : []
  } catch { /* 后端不可达时保持骨架 */ }
  try {
    const r = await worldWhatIf({ 动力电池: 0.4, PLC: 0.6 }, '动力电池', ['PLC'])
    suggestion.value = `世界模型建议: 先「${r.recommended}」《动力电池》(预计掌握度 +${r.expectedGain})`
  } catch { suggestion.value = '' }
})
</script>

<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="8">
        <a-card title="综合掌握度">
          <a-statistic :value="overall ?? '—'" :precision="3" style="font-size:32px" />
          <div class="text-secondary">BKT+DKT 融合估计</div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="薄弱知识点 TOP3">
          <div v-for="w in weak" :key="w.kp" class="card-mastery" style="padding:6px 12px; margin-bottom:8px">
            KP#{{ w.kp }} — 掌握度 {{ (w.mastery * 100).toFixed(1) }}%
          </div>
          <div v-if="!weak.length" class="text-secondary">暂无数据</div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="世界模型推演建议">
          <a-alert v-if="suggestion" type="success" :message="suggestion" />
          <div v-else class="text-secondary">推演服务暂不可达</div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>
