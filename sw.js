const cacheName = 'tadio-cache-v2';
const assetsToCache = [
  './',
  'index.html',
  'tvonline.html',
  'radio.html',
  'https://img2.pic.in.th/pic/1000021059.png',
  'https://fonts.googleapis.com/css2?family=Kanit:wght@300;400;500&display=swap'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(cacheName)
      .then((cache) => {
        return cache.addAll(assetsToCache);
      })
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== cacheName) {
            return caches.delete(key);
          }
        })
      );
    })
  );
});

self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request)
      .then((response) => {
        return response || fetch(event.request);
      })
  );
});
