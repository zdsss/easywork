import { onMounted, onUnmounted } from 'vue'

// Characters considered "printable" for scan buffer accumulation
function isPrintable(key) {
  return key.length === 1
}

/**
 * Hardware input layer for industrial PDA / scan gun / keypad terminals.
 *
 * Scan gun detection:
 *   - Each printable char is timestamped into scanBuffer
 *   - Gap >= 50ms → buffer reset
 *   - On Enter: if buffer.length >= 4 and all chars arrived in < 50ms → scan gun
 *     → preventDefault, clear any focused input, call onScan(barcode)
 *   - Uses capture phase so scan gun Enter fires before dialog before-close
 *
 * Non-input-focused navigation/shortcuts:
 *   - Arrow keys → onNavigate(dir)
 *   - ESC → onBack()
 *   - 0-9 → onShortcut(key)
 *   - Enter → onConfirm()
 *
 * When an input is focused, only scan gun detection runs.
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

    // When an input/textarea is focused, let ALL keys pass through natively.
    // Scan gun detection is only meaningful when no input is focused (e.g. list
    // view, detail view) – ScanView relies on its own @keyup.enter handler.
    if (isInputFocused()) {
      clearScanBuffer()
      return
    }

    // --- Scan gun detection (only when no input is focused) ---
    if (isPrintable(key)) {
      const gap = now - lastCharTime
      if (lastCharTime > 0 && gap >= SCAN_THRESHOLD_MS) {
        // Slow keystroke – reset buffer
        clearScanBuffer()
      }
      scanBuffer.push({ char: key, time: now })
      lastCharTime = now
      return
    }

    if (key === 'Enter') {
      const buf = scanBuffer
      clearScanBuffer()

      if (buf.length >= SCAN_MIN_LENGTH) {
        // Verify all gaps were fast
        let allFast = true
        for (let i = 1; i < buf.length; i++) {
          if (buf[i].time - buf[i - 1].time >= SCAN_THRESHOLD_MS) {
            allFast = false
            break
          }
        }

        if (allFast) {
          // Scan gun confirmed (no input was focused)
          e.preventDefault()
          e.stopPropagation()
          const barcode = buf.map(b => b.char).join('')
          if (onScan) onScan(barcode)
          return
        }
      }

      // Not a scan gun Enter – treat as confirm
      e.preventDefault()
      if (onConfirm) onConfirm()
      return
    }

    // Non-printable, non-Enter key → reset scan buffer
    clearScanBuffer()

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
