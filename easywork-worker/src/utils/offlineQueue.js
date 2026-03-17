const DB_NAME = 'easywork-offline'
const STORE = 'queue'

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = (e) => {
      e.target.result.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true })
    }
    req.onsuccess = (e) => resolve(e.target.result)
    req.onerror = (e) => reject(e.target.error)
  })
}

export async function enqueue(item) {
  // Assign a stable idempotency key at enqueue time so that retries on
  // the same queued item always carry the same key, enabling server-side dedup.
  const idempotencyKey = item.idempotencyKey ?? crypto.randomUUID()
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const req = store.add({ ...item, idempotencyKey, timestamp: Date.now() })
    req.onsuccess = () => resolve(req.result)
    req.onerror = (e) => reject(e.target.error)
  })
}

export async function getQueue() {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly')
    const store = tx.objectStore(STORE)
    const req = store.getAll()
    req.onsuccess = () => resolve(req.result)
    req.onerror = (e) => reject(e.target.error)
  })
}

export async function dequeue(id) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const req = store.delete(id)
    req.onsuccess = () => resolve()
    req.onerror = (e) => reject(e.target.error)
  })
}

/**
 * Process the offline queue in order.
 * Items with a `chainId` are treated as a chain: if one item in a chain fails,
 * all subsequent items with the same chainId are skipped (removed from queue).
 *
 * Returns a summary: { processed: number, failed: Array<{label, reason}>, skipped: number }
 * Callers should surface `failed` items to the user.
 */
export async function processQueue(httpInstance) {
  let items
  try {
    items = await getQueue()
  } catch {
    return { processed: 0, failed: [], skipped: 0 }
  }

  const failedChains = new Set()
  // Map chainId → failure reason for user-facing messages
  const chainFailureReasons = new Map()
  let processed = 0
  let skipped = 0
  const failed = []

  for (const item of items) {
    // If this item belongs to a failed chain, skip it (orphan task)
    if (item.chainId && failedChains.has(item.chainId)) {
      await dequeue(item.id)
      skipped++
      continue
    }

    try {
      const headers = item.idempotencyKey
        ? { 'Idempotency-Key': item.idempotencyKey }
        : undefined
      await httpInstance({ method: item.method, url: item.url, data: item.body, headers })
      await dequeue(item.id)
      processed++
    } catch (e) {
      if (e.response) {
        // Server rejected (4xx/5xx) - discard to avoid infinite retry
        const reason = e.response.data?.message || `服务器错误 ${e.response.status}`
        await dequeue(item.id)
        failed.push({ label: item.label || item.url, reason })
        if (item.chainId) {
          failedChains.add(item.chainId)
          chainFailureReasons.set(item.chainId, reason)
        }
      } else {
        // Network error: keep in queue for next retry, mark chain as failed
        failed.push({ label: item.label || item.url, reason: '网络错误，将在下次联网时重试' })
        if (item.chainId) {
          failedChains.add(item.chainId)
          chainFailureReasons.set(item.chainId, '网络错误')
        }
      }
    }
  }

  if (skipped > 0) {
    console.warn(`[offlineQueue] ${skipped} item(s) skipped due to chain failures.`)
  }

  return { processed, failed, skipped }
}
