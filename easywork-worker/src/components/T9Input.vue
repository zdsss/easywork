<template>
  <div class="t9-input">
    <van-field :model-value="value" label="输入" readonly />
    <div class="t9-keyboard">
      <van-button v-for="key in ['1','2','3','4','5','6','7','8','9','0']" :key="key" @click="press(key)">
        {{ key }}
      </van-button>
      <van-button @click="toggleCase">{{ uppercase ? 'ABC' : 'abc' }}</van-button>
      <van-button @click="backspace">删除</van-button>
      <van-button @click="clear" @touchstart="startLongPress" @touchend="cancelLongPress">清空</van-button>
    </div>
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { useT9Input } from '@/composables/useT9Input'

const props = defineProps({
  modelValue: { type: String, default: undefined },
})
const emit = defineEmits(['update:modelValue'])

const { value, press, backspace, clear, toggleCase, uppercase } = useT9Input()

// Sync external v-model to internal value
watch(() => props.modelValue, (v) => {
  if (v !== undefined && v !== value.value) {
    value.value = v
  }
})

// Emit internal changes to parent
watch(value, (v) => {
  emit('update:modelValue', v)
})

let longPressTimer = null

function startLongPress() {
  longPressTimer = setTimeout(clear, 500)
}

function cancelLongPress() {
  if (longPressTimer) clearTimeout(longPressTimer)
}
</script>

<style scoped>
.t9-keyboard {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px;
}
</style>
