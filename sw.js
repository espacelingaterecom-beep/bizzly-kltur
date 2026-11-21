/* Bizzly — service worker : l'appli s'ouvre et fonctionne sans internet.
   Pour publier une mise à jour, changez le numéro de VERSION ci-dessous. */
const VERSION = "bizzly-v5";
const FICHIERS = ["./", "index.html", "manifest.webmanifest", "icon-192.png", "icon-512.png", "icon-maskable-512.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(FICHIERS)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(noms => Promise.all(noms.filter(n => n !== VERSION).map(n => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});
self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then(enCache =>
      enCache ||
      fetch(e.request).then(rep => {
        if (new URL(e.request.url).origin === location.origin) {
          const copie = rep.clone();
          caches.open(VERSION).then(c => c.put(e.request, copie));
        }
        return rep;
      }).catch(() => caches.match("index.html"))
    )
  );
});
