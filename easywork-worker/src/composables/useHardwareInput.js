import { onMounted, onUnmounted } from 'vue'

// Characters considered "printable" for scan buffer accumulation
function isPrintable(key) {
  return key.length === 1
}

/**
 * Hardware input layer for industrial PDA / scan gun / keypad terminals.
 *
 * Scan gun detection:
 *   - Each printable char is timestamped into scanBuffer (regardless of input focus)
 *   - Gap >= 50ms between chars → buffer reset
 *   - On Enter: if buffer.length >= 4 and all chars arrived in < 50ms → scan gun
 *     → preventDefault+stopPropagation, call onScan(barcode)
 *   - Uses capture phase so scan gun Enter fires before dialog before-close
 *
 * When an input is focused:
 *   - Printable chars still accumulate in buffer but are NOT prevented (pass to input natively)
 *   - Scan gun Enter IS intercepted (even when input focused) to catch scan-while-typing scenarios
 *   - Arrow keys, Escape, and regular Enter fall through to the input natively
 *
 * When NO input is focused:
 *   - Arrow keys → onNavigate(dir)
 *   - ESC → onBack()
 *   - Backspace → onShortcut('Backspace')
 *   - Enter → onConfirm()
 *   - 0-9 → onShortcut(key)  [separate listener]
 */
export function useHardwareInput({
  onNavigate,
  onConfirm,
  onBack,
  onShortcut,
  onScan,
} = {}) {
  const SCAN_THRESHOLD_MS = 50
  const SCAN_MIN_LENGTH = 4

  let scanBuffer = []
  let lastCharTime = 0

  function isInputFocused() {
    const tag = document.activeElement?.tagName?.toLowerCase()
    return tag === 'input' || tag === 'textarea' || document.activeElement?.isContentEditable
  }

  function clearScanBuffer() {
    scanBuffer = []
    lastCharTime = 0
  }

  function handleKeydown(e) {
    const now = Date.now()
    const key = e.key
    const inputFocused = isInputFocused()

    // --- Scan gun detection: accumulate printable chars regardless of input focus ---
    if (isPrintable(key)) {
      const gap = now - lastCharTime
      if (lastCharTime > 0 && gap >= SCAN_THRESHOLD_MS) {
        // Slow keystroke (real typing) → reset buffer
        clearScanBuffer()
      }
      scanBuffer.push({ char: key, time: now })
      lastCharTime = now
      // When input is focused: let chars fall through natively (no preventDefault)
      // The buffer still accumulates so we can detect a scan gun on Enter
      return
    }

    if (key === 'Enter') {
      const buf = scanBuffer
      clearScanBuffer()

      if (buf.length >= SCAN_MIN_LENGTH) {
        // Verify all inter-char gaps were fast
        let allFast = true
        for (let i = 1; i < buf.length; i++) {
          if (buf[i].time - buf[i - 1].time >= SCAN_THRESHOLD_MS) {
            allFast = false
            break
          }
        }

        if (allFast) {
          // Scan gun confirmed – intercept even when input is focused
          e.preventDefault()
          e.stopPropagation()
          const barcode = buf.map(b => b.char).join('')
          if (onScan) onScan(barcode)
          return
        }
      }

      // Not a scan gun Enter
      if (inputFocused) return // Let Enter fall through to input (form submit etc.)
      e.preventDefault()
      if (onConfirm) onConfirm()
      return
    }

    // Non-printable, non-Enter key → reset scan buffer
    clearScanBuffer()

    // When input is focused: let all other keys (arrows, Backspace) pass through natively
    if (inputFocused) return

    // Navigation shortcuts (only when no input is focused)
    if (key === 'ArrowUp') {
      e.preventDefault()
      if (onNavigate) onNavigate('up')
    } else if (key === 'ArrowDown') {
      e.preventDefault()
      if (onNavigate) onNavigate('down')
    } else if (key === 'ArrowLeft') {
      e.preventDefault()
      if (onNavigate) onNavigate('left')
    } else if (key === 'ArrowRight') {
      e.preventDefault()
      if (onNavigate) onNavigate('right')
    } else if (key === 'Escape') {
      e.preventDefault()
      if (onBack) onBack()
    } else if (key === 'Backspace') {
      e.preventDefault()
      if (onShortcut) onShortcut('Backspace')
    }
  }

  function handleKeydownShortcut(e) {
    // Shortcut digits – separate listener so shortcuts can be toggled
    if (isInputFocused()) return
    if (/^[0-9]$/.test(e.key)) {
      e.preventDefault()
      if (onShortcut) onShortcut(e.key)
    }
  }

  onMounted(() => {
    // Capture phase: scan gun detection takes priority over everything
    document.addEventListener('keydown', handleKeydown, true)
    document.addEventListener('keydown', handleKeydownShortcut, false)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeydown, true)
    document.removeEventListener('keydown', handleKeydownShortcut, false)
  })
}
