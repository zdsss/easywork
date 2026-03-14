<template>
  <div class="scan-view">
    <van-nav-bar title="扫码操作" />

    <!-- 扫码模式切换 -->
    <div class="mode-toggle">
      <van-button
        :type="scanMode === 'start' ? 'primary' : 'default'"
        size="small"
        @click="scanMode = 'start'"
      >开工模式</van-button>
      <van-button
        :type="scanMode === 'report' ? 'success' : 'default'"
        size="small"
        @click="scanMode = 'report'"
      >报工模式</van-button>
    </div>

    <div class="scan-content">
      <!-- 摄像头扫码区域 -->
      <div v-if="cameraActive" class="camera-section">
        <video ref="videoRef" class="camera-video" autoplay playsinline muted />
        <div class="camera-overlay">
          <div class="scan-frame" />
          <p class="camera-hint">将条码对准扫描框</p>
        </div>
        <van-button block type="danger" @click="stopCamera" style="margin-top: 8px">关闭摄像头</van-button>
      </div>

      <div v-if="!cameraActive">
        <van-cell-group inset title="输入工单条码">
          <van-field
            v-model="barcode"
            label="条码"
            placeholder="请输入或扫描工单号"
            clearable
            autofocus
            @keyup.enter="handleScanStart"
          />
        </van-cell-group>

        <div class="action-buttons">
          <van-button
            v-if="cameraSupported"
            type="default"
            block
            icon="scan"
            @click="startCamera"
            class="camera-button"
          >
            摄像头扫码
          </van-button>
          <van-button
            type="primary"
            block
            :loading="startLoading"
            :disabled="!barcode"
            @click="handleScanStart"
          >
            扫码开工
          </van-button>
          <van-button
            type="success"
            block
            :loading="reportLoading"
            :disabled="!barcode"
            @click="handleScanReport"
            class="report-button"
          >
            扫码报工
          </van-button>
        </div>
      </div>

      <van-divider v-if="result">操作结果</van-divider>

      <van-cell-group v-if="result" inset>
        <van-cell title="工单号" :value="result.orderNumber" />
        <van-cell title="产品" :value="result.productName" />
        <van-cell title="当前工序" :value="`${result.processNumber || '-'} - ${result.processName || '-'}`" />
        <van-cell title="工序状态" :value="statusLabel(result.status)" />
        <van-cell title="完成数量" :value="`${result.completedQuantity} / ${result.plannedQuantity}`" />
      </van-cell-group>
    </div>

    <KeyHints :hints="scanHints" />

    <van-tabbar route>
      <van-tabbar-item icon="todo-list-o" to="/workorders">工单</van-tabbar-item>
      <van-tabbar-item icon="scan" to="/scan">扫码</van-tabbar-item>
      <van-tabbar-item icon="phone-o" to="/call">呼叫</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted, onMounted } from 'vue'
import { showSuccessToast, showFailToast, showToast } from 'vant'
import { scanStart, scanReport } from '@/api/scan'
import { useScanStore } from '@/stores/scan'
import { useHardwareInput } from '@/composables/useHardwareInput'
import KeyHints from '@/components/KeyHints.vue'

const scanStore = useScanStore()

const barcode = ref('')
const startLoading = ref(false)
const reportLoading = ref(false)
const result = ref(null)
const scanMode = ref('start') // 'start' | 'report'

const scanHints = computed(() => [
  { key: 'Scan', label: `自动${scanMode.value === 'start' ? '开工' : '报工'}` },
  { key: 'Tab', label: '切换模式' },
])

// Tab key toggles scan mode
function handleTabKey(e) {
  if (e.key !== 'Tab') return
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea') return
  e.preventDefault()
  scanMode.value = scanMode.value === 'start' ? 'report' : 'start'
}
onMounted(() => document.addEventListener('keydown', handleTabKey))
onUnmounted(() => document.removeEventListener('keydown', handleTabKey))

useHardwareInput({
  async onScan(barcodeValue) {
    barcode.value = barcodeValue
    if (scanMode.value === 'start') {
      await handleScanStart()
    } else {
      await handleScanReport()
    }
  },
})

// Camera state
const cameraSupported = ref('BarcodeDetector' in window)
const cameraActive = ref(false)
const videoRef = ref(null)
let mediaStream = null
let detectorInterval = null

const statusLabel = (status) => {
  const map = {
    NOT_STARTED: '未开工',
    STARTED: '进行中',
    REPORTED: '已报工',
    INSPECT_PASSED: '质检通过',
    INSPECT_FAILED: '质检失败',
    COMPLETED: '已完成',
  }
  return map[status] || status
}

function playFeedback(success) {
  if (success) {
    if ('vibrate' in navigator) navigator.vibrate(200)
    const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBSuBzvLZiTYIGGi77eeeTRAMUKfj8LZjHAY4ktfyzHksBSR3x/DdkEAKFF606+uoVRQKRp/g8r5sIQUrgs7y2Ik2CBhpvO3nnk0QDFA=')
    audio.play().catch(() => {})
  } else {
    if ('vibrate' in navigator) navigator.vibrate([100, 50, 100])
  }
}

async function startCamera() {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' },
    })
    cameraActive.value = true
    // Wait for video element to be rendered
    setTimeout(() => {
      if (videoRef.value) {
        videoRef.value.srcObject = mediaStream
        startBarcodeDetection()
      }
    }, 100)
  } catch (e) {
    cameraSupported.value = false
    showToast('无法访问摄像头，请使用文本输入')
  }
}

function startBarcodeDetection() {
  if (!('BarcodeDetector' in window)) return
  const detector = new window.BarcodeDetector({ formats: ['qr_code', 'code_128', 'code_39', 'ean_13', 'ean_8'] })
  detectorInterval = setInterval(async () => {
    if (!videoRef.value || videoRef.value.readyState < 2) return
    try {
      const barcodes = await detector.detect(videoRef.value)
      if (barcodes.length > 0) {
        const detected = barcodes[0].rawValue
        barcode.value = detected
        stopCamera()
        showToast(`已识别：${detected}`)
        playFeedback(true)
      }
    } catch {
      // detection frame failed, continue
    }
  }, 300)
}

function stopCamera() {
  if (detectorInterval) {
    clearInterval(detectorInterval)
    detectorInterval = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop())
    mediaStream = null
  }
  cameraActive.value = false
}

onUnmounted(stopCamera)

const handleScanStart = async () => {
  if (!barcode.value) return
  startLoading.value = true
  result.value = null
  scanStore.setScanning()
  try {
    result.value = await scanStart(barcode.value.trim())
    scanStore.setSuccess()
    playFeedback(true)
    showSuccessToast('开工成功')
  } catch (e) {
    scanStore.setError()
    playFeedback(false)
  } finally {
    startLoading.value = false
  }
}

const handleScanReport = async () => {
  if (!barcode.value) return
  reportLoading.value = true
  result.value = null
  scanStore.setScanning()
  try {
    result.value = await scanReport(barcode.value.trim())
    scanStore.setSuccess()
    playFeedback(true)
    showSuccessToast('报工成功')
  } catch (e) {
    scanStore.setError()
    playFeedback(false)
  } finally {
    reportLoading.value = false
  }
}
</script>

<style scoped>
.scan-view {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 50px;
}
.mode-toggle {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.scan-content {
  padding: 16px;
}
.action-buttons {
  margin-top: 24px;
}
.camera-button {
  margin-bottom: 12px;
}
.report-button {
  margin-top: 12px;
}
.camera-section {
  margin-bottom: 16px;
}
.camera-video {
  width: 100%;
  max-height: 320px;
  border-radius: 8px;
  object-fit: cover;
  background: #000;
}
.camera-overlay {
  position: relative;
  text-align: center;
  margin-top: 8px;
}
.scan-frame {
  display: inline-block;
  width: 200px;
  height: 200px;
  border: 2px solid #1989fa;
  border-radius: 4px;
  position: absolute;
  top: -280px;
  left: 50%;
  transform: translateX(-50%);
  pointer-events: none;
}
.camera-hint {
  font-size: 13px;
  color: #666;
  margin-top: 4px;
}
</style>
