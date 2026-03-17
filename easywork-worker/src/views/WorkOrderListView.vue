<template>
  <div class="page">
    <van-nav-bar :title="navTitle" fixed>
      <template #right>
        <van-button v-if="!batchMode" size="small" type="primary" @click="batchMode = true">批量</van-button>
        <van-button v-else size="small" @click="exitBatchMode">取消</van-button>
      </template>
    </van-nav-bar>

    <div class="content">
      <div v-if="batchMode" class="batch-toolbar">
        <van-button size="small" @click="toggleSelectAll">{{ allSelected ? '取消全选' : '全选' }}</van-button>
        <van-button size="small" type="primary" :disabled="selectedCount === 0" @click="batchStart">批量开工</van-button>
        <van-button size="small" type="success" :disabled="selectedCount === 0" @click="batchReport">批量报工</van-button>
      </div>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <div
            v-for="(order, idx) in list"
            :key="order.id"
            :class="['order-card', !batchMode && idx === currentIndex ? 'focused' : '']"
            :data-index="idx"
            @click="handleCardClick(order, idx)"
          >
            <van-checkbox v-if="batchMode" v-model="order.checked" @click.stop />
            <div class="card-header">
              <span class="order-number">{{ order.orderNumber }}</span>
              <van-tag :type="getStatusTagType(order)">{{ getStatusLabel(order) }}</van-tag>
            </div>
            <div class="card-body">
              <div class="info-row">
                <span class="label">产品：</span>
                <span>{{ order.productName }}</span>
              </div>
              <div class="info-row">
                <span class="label">计划数量：</span>
                <span>{{ order.plannedQuantity }}</span>
                <span class="label" style="margin-left: 16px">完成：</span>
                <span>{{ order.completedQuantity }}</span>
              </div>
              <div class="info-row">
                <span class="label">优先级：</span>
                <van-tag
                  :type="order.priority >= 8 ? 'danger' : order.priority >= 5 ? 'warning' : 'default'"
                  size="small"
                >
                  P{{ order.priority }}
                </van-tag>
                <span class="label" style="margin-left: 16px">计划开始：</span>
                <span>{{ formatDate(order.plannedStartTime) }}</span>
              </div>
            </div>
          </div>

          <van-empty v-if="!loading && list.length === 0" description="暂无工单" />
        </van-list>
      </van-pull-refresh>
    </div>

    <KeyHints :hints="keyHints" />

    <van-tabbar route>
      <van-tabbar-item icon="orders-o" to="/workorders">工单</van-tabbar-item>
      <van-tabbar-item icon="scan" to="/scan">扫码</van-tabbar-item>
      <van-tabbar-item icon="phone-o" to="/call">呼叫</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { getWorkOrders } from '@/api/workorder'
import { showToast, showConfirmDialog } from 'vant'
import { getStatusLabel, getStatusTagType } from '@/utils/statusLabel'
import { useHardwareInput } from '@/composables/useHardwareInput'
import { scanStart } from '@/api/scan'
import KeyHints from '@/components/KeyHints.vue'
import http from '@/api/http'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const list = ref([])
const batchMode = ref(false)
const currentIndex = ref(0)
let allOrders = []
const pageSize = 10

const selectedCount = computed(() => list.value.filter(o => o.checked).length)
const allSelected = computed(() => list.value.length > 0 && list.value.every(o => o.checked))
const navTitle = computed(() => {
  if (!batchMode.value && list.value.length > 0) {
    return `我的工单 (${currentIndex.value + 1}/${list.value.length})`
  }
  return '我的工单'
})

function scrollToFocused() {
  nextTick(() => {
    const el = document.querySelector('.order-card.focused')
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

const keyHints = computed(() => {
  if (batchMode.value) return [
    { key: '7', label: '全部开工' },
    { key: '8', label: '全部报工' },
  ]
  return [
    { key: '↑↓', label: '切换' },
    { key: 'Enter', label: '进入' },
    { key: '7', label: '全部开工' },
    { key: '8', label: '全部报工' },
    { key: 'Scan', label: '扫码开工' },
  ]
})

useHardwareInput({
  onNavigate(dir) {
    if (batchMode.value) return
    if (dir === 'up' || dir === 'left') {
      if (currentIndex.value > 0) {
        currentIndex.value--
        scrollToFocused()
      }
    } else if (dir === 'down' || dir === 'right') {
      if (currentIndex.value < list.value.length - 1) {
        currentIndex.value++
        scrollToFocused()
      }
    }
  },
  onConfirm() {
    if (batchMode.value) return
    const order = list.value[currentIndex.value]
    if (order) goDetail(order.id)
  },
  onShortcut(key) {
    if (key === '7') {
      // 全部开工：一键开工所有可见工单中 NOT_STARTED 的工序
      quickBatchStart()
    } else if (key === '8') {
      // 全部报工：一键报工所有可见工单中 STARTED 的工序
      quickBatchReport()
    }
  },
  async onScan(barcode) {
    if (batchMode.value) return
    try {
      const result = await scanStart(barcode)
      showToast({ type: 'success', message: `开工成功：${result.orderNumber || barcode}` })
      await onRefresh()
      // Navigate to the work order detail if we can find it
      const targetId = result.orderId
        ?? list.value.find(o => o.orderNumber === result.orderNumber)?.id
      if (targetId) goDetail(targetId)
    } catch {
      showToast({ type: 'fail', message: '扫码开工失败，请检查条码' })
    }
  },
})

function toggleSelectAll() {
  const newValue = !allSelected.value
  list.value.forEach(o => o.checked = newValue)
}

function exitBatchMode() {
  batchMode.value = false
  list.value.forEach(o => o.checked = false)
}

function handleCardClick(order, idx) {
  if (batchMode.value) {
    order.checked = !order.checked
  } else {
    currentIndex.value = idx
    goDetail(order.id)
  }
}

// Quick-batch: 全部开工 / 全部报工 triggered by hardware shortcut keys (7/8)
// Operates on ALL visible work orders without entering batch-select mode.
async function quickBatchStart() {
  const operationIds = list.value.flatMap(o =>
    (o.operations || []).filter(op => op.status === 'NOT_STARTED').map(op => op.id)
  )
  if (operationIds.length === 0) {
    showToast('没有可开工的工序')
    return
  }
  try {
    await showConfirmDialog({ title: '全部开工', message: `将开工全部 ${operationIds.length} 个工序` })
    const idempotencyKey = crypto.randomUUID()
    await http.post('/device/batch/start', { operationIds }, { headers: { 'Idempotency-Key': idempotencyKey } })
    showToast('全部开工成功')
    onRefresh()
  } catch {}
}

async function quickBatchReport() {
  const operationIds = list.value.flatMap(o =>
    (o.operations || []).filter(op => op.status === 'STARTED').map(op => op.id)
  )
  if (operationIds.length === 0) {
    showToast('没有可报工的工序')
    return
  }
  try {
    await showConfirmDialog({ title: '全部报工', message: `将报工全部 ${operationIds.length} 个工序` })
    const idempotencyKey = crypto.randomUUID()
    await http.post('/device/batch/report', { operationIds }, { headers: { 'Idempotency-Key': idempotencyKey } })
    showToast('全部报工成功')
    onRefresh()
  } catch {}
}

async function batchStart() {
  const selected = list.value.filter(o => o.checked)
  const operationIds = selected.flatMap(o =>
    (o.operations || []).filter(op => op.status === 'NOT_STARTED').map(op => op.id)
  )

  if (operationIds.length === 0) {
    showToast('没有可开工的工序')
    return
  }

  try {
    await showConfirmDialog({ title: '确认批量开工', message: `将开工 ${operationIds.length} 个工序` })
    const idempotencyKey = crypto.randomUUID()
    await http.post('/device/batch/start', { operationIds }, { headers: { 'Idempotency-Key': idempotencyKey } })
    showToast('批量开工成功')
    exitBatchMode()
    onRefresh()
  } catch {}
}

async function batchReport() {
  const selected = list.value.filter(o => o.checked)
  const operationIds = selected.flatMap(o =>
    (o.operations || []).filter(op => op.status === 'STARTED').map(op => op.id)
  )

  if (operationIds.length === 0) {
    showToast('没有可报工的工序')
    return
  }

  try {
    await showConfirmDialog({ title: '确认批量报工', message: `将报工 ${operationIds.length} 个工序` })
    const idempotencyKey = crypto.randomUUID()
    await http.post('/device/batch/report', { operationIds }, { headers: { 'Idempotency-Key': idempotencyKey } })
    showToast('批量报工成功')
    exitBatchMode()
    onRefresh()
  } catch {}
}

function formatDate(val) {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function onLoad() {
  if (allOrders.length === 0 && !finished.value) {
    try {
      const data = await getWorkOrders()
      allOrders = (Array.isArray(data) ? data : []).map(o => ({ ...o, checked: false }))
    } catch {}
  }

  const start = list.value.length
  const nextPage = allOrders.slice(start, start + pageSize)
  list.value = [...list.value, ...nextPage]

  if (list.value.length >= allOrders.length) {
    finished.value = true
  }
  loading.value = false
}

async function onRefresh() {
  allOrders = []
  list.value = []
  finished.value = false
  currentIndex.value = 0
  await onLoad()
  refreshing.value = false
}

function goDetail(id) {
  router.push(`/workorders/${id}`)
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.content {
  padding-top: 46px;
  padding-bottom: 50px;
}

.order-card {
  margin: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.15s;
}

.order-card.focused {
  border-color: #1989fa;
  box-shadow: 0 2px 8px rgba(25, 137, 250, 0.2);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.order-number {
  font-weight: bold;
  font-size: 15px;
  color: #333;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #333;
}

.label {
  color: #999;
}

.batch-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
}

.order-card {
  position: relative;
}

.order-card .van-checkbox {
  position: absolute;
  left: 16px;
  top: 16px;
}

.order-card.batch-mode {
  padding-left: 48px;
}
</style>
