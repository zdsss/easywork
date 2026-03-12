<template>
  <div class="page">
    <van-nav-bar title="呼叫" fixed />

    <div class="content">
      <van-cell-group inset title="呼叫类型" style="margin-top: 12px">
        <van-radio-group v-model="form.callType">
          <van-cell
            v-for="t in callTypes"
            :key="t.value"
            :title="t.label"
            :label="t.desc"
            clickable
            @click="form.callType = t.value"
          >
            <template #right-icon>
              <van-radio :name="t.value" />
            </template>
          </van-cell>
        </van-radio-group>
      </van-cell-group>

      <van-cell-group inset title="工单（必填）" style="margin-top: 12px">
        <van-field
          v-model="form.workOrderId"
          label="工单ID"
          placeholder="必填，关联工单ID"
          type="number"
          required
        />
      </van-cell-group>

      <van-cell-group inset title="描述" style="margin-top: 12px">
        <van-field
          v-model="form.description"
          type="textarea"
          placeholder="请描述问题或需求..."
          :rows="4"
          autosize
        />
      </van-cell-group>

      <div style="margin: 24px 16px">
        <van-button
          round
          block
          type="primary"
          :loading="submitting"
          loading-text="呼叫中..."
          @click="handleCall"
        >
          发起呼叫
        </van-button>
      </div>
    </div>

    <van-tabbar route>
      <van-tabbar-item icon="orders-o" to="/workorders">工单</van-tabbar-item>
      <van-tabbar-item icon="scan" to="/scan">扫码</van-tabbar-item>
      <van-tabbar-item icon="phone-o" to="/call">呼叫</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { showToast } from 'vant'
import { callAndon, callInspection, callTransport } from '@/api/call'

const submitting = ref(false)

const callTypes = [
  { value: 'andon', label: 'Andon 呼叫', desc: '设备故障、紧急停线等问题' },
  { value: 'inspection', label: '质检呼叫', desc: '请求质检人员到场检验' },
  { value: 'transport', label: '搬运呼叫', desc: '物料搬运或成品转移' },
]

const form = reactive({
  callType: 'andon',
  workOrderId: '',
  description: '',
})

async function handleCall() {
  if (!form.workOrderId) {
    showToast('请填写工单ID（必填）')
    return
  }
  submitting.value = true
  try {
    const payload = {
      workOrderId: Number(form.workOrderId),
      ...(form.description && { description: form.description }),
    }

    if (form.callType === 'andon') {
      await callAndon(payload)
    } else if (form.callType === 'inspection') {
      await callInspection(payload)
    } else {
      await callTransport(payload)
    }

    showToast({ type: 'success', message: '呼叫已发送' })
    form.description = ''
    form.workOrderId = ''
  } catch {
    // handled in interceptor
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.content {
  padding-top: 46px;
  padding-bottom: 60px;
}
</style>
