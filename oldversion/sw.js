var CACHE_NAME = 'tadioplus-cache-v1';
var urlsToCache = [
  './',
  './index.html',
  './tvonline.html',
  './radio.html'
];

self.addEventListener('install', function(event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function(cache) {
      return cache.addAll(urlsToCache);
    })
  );
});

self.addEventListener('fetch', function(event) {
  event.respondWith(
    caches.match(event.request).then(function(response) {
      // คืนค่าไฟล์จากแคชถ้ามี ถ้าไม่มีให้ดึงจากเน็ตตามปกติ
      return response || fetch(event.request);
    }).catch(function() {
      // ป้องกัน Error กรณีออฟไลน์และไม่มีไฟล์ในแคช
      return new Response('Offline');
    })
  );
});