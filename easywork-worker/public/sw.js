const CACHE_NAME = 'easywork-v1'
const STATIC_ASSETS = ['/', '/index.html']

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE_NAME).then((c) => c.addAll(STATIC_ASSETS))
  )
  self.skipWaiting()
})

self.addEventListener('activate', (e) => {
  e.waitUntil(clients.claim())
})

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url)
  if (url.origin !== location.origin) return

  if (e.request.method === 'GET') {
    if (url.pathname.startsWith('/api/')) {
      // Network-first for API requests
      e.respondWith(
        fetch(e.request)
          .then((res) => {
            const clone = res.clone()
            caches.open(CACHE_NAME).then((c) => c.put(e.request, clone))
            return res
          })
          .catch(() => caches.match(e.request))
      )
    } else {
      // Cache-first for static assets
      e.respondWith(
        caches.match(e.request).then((cached) => cached || fetch(e.request))
      )
    }
  }
  // POST/PUT/DELETE: let app handle queueing, don't intercept
})
