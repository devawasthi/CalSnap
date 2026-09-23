// Cache only the offline message. Authenticated pages, photos and API responses never enter a cache.
const CACHE='calsnap-offline-v1';
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(c=>c.add('/offline.html')));self.skipWaiting();});
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{if(event.request.mode==='navigate')event.respondWith(fetch(event.request).catch(()=>caches.match('/offline.html')));});
