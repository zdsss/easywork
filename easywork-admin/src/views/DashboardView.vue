<template>
  <div>
    <el-row :gutter="16" class="stat-row">
      <el-col :span="24 / stats.length" v-for="stat in stats" :key="stat.label">
        <el-card shadow="never">
          <el-statistic :title="stat.label" :value="stat.value" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>整体完成率</span>
      </template>
      <div style="padding: 8px 0">
        <el-progress
          :percentage="completionRate"
          :format="(p) => p + '%'"
          status="success"
          :stroke-width="18"
        />
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>工单类型分布</span>
      </template>
      <el-table :data="typeStats" stripe>
        <el-table-column prop="orderType" label="工单类型" />
        <el-table-column prop="count" label="总数" width="100" />
        <el-table-column prop="completedCount" label="已完成" width="100" />
        <el-table-column label="完成率" width="120">
          <template #default="{ row }">
            <span>{{ row.count > 0 ? ((row.completedCount / row.count) * 100).toFixed(1) + '%' : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>近 7 天质检趋势</span>
      </template>
      <div ref="chartRef" style="height: 280px" />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>工人产出统计</span>
      </template>
      <el-table :data="workerOutput" v-loading="loading" stripe>
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="employeeNumber" label="员工号" />
        <el-table-column prop="reportCount" label="报工次数" />
        <el-table-column prop="totalReported" label="总报工量" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboard, getInspectionTrend } from '@/api/statistics'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const loading = ref(false)
const stats = ref([
  { label: '总工单数', value: 0 },
  { label: '进行中', value: 0 },
  { label: '已报工', value: 0 },
  { label: '已完成', value: 0 },
  { label: '未开始', value: 0 },
])
const completionRate = ref(0)
const typeStats = ref([])
const workerOutput = ref([])
const chartRef = ref(null)
let chartInstance = null

function initChart(trendData) {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  const dates = trendData.map((d) => d.date)
  const passed = trendData.map((d) => Number(d.passed ?? 0))
  const failed = trendData.map((d) => Number(d.failed ?? 0))
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['质检通过', '质检不通过'] },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '质检通过',
        type: 'line',
        data: passed,
        itemStyle: { color: '#67c23a' },
        smooth: true,
      },
      {
        name: '质检不通过',
        type: 'line',
        data: failed,
        itemStyle: { color: '#f56c6c' },
        smooth: true,
      },
    ],
  })
}

async function loadData() {
  loading.value = true
  try {
    const [data, trend] = await Promise.all([
      getDashboard(),
      getInspectionTrend(7).catch(() => []),
    ])
    if (data) {
      stats.value = [
        { label: '总工单数', value: data.totalWorkOrders ?? 0 },
        { label: '进行中', value: data.startedCount ?? 0 },
        { label: '已报工', value: data.reportedCount ?? 0 },
        { label: '已完成', value: data.completedCount ?? 0 },
        { label: '未开始', value: data.notStartedCount ?? 0 },
      ]
      const rate = data.overallCompletionRate
      completionRate.value = rate != null ? Math.round(Number(rate) * 100) : 0
      typeStats.value = data.typeStats ?? []
      workerOutput.value = data.workerStats ?? []
    }
    await nextTick()
    initChart(Array.isArray(trend) ? trend : [])
  } catch {
    // error handled in http interceptor
  } finally {
    loading.value = false
  }
}

function handleResize() {
  chartInstance?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 0;
}
</style>
