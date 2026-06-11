import { ref, onBeforeUnmount, getCurrentInstance } from 'vue'

let nextId = 1

export function useOverlayParticles() {
  const particles = ref([])
  let timers = []

  const prefersReduced = () =>
    window.matchMedia('(prefers-reduced-motion: reduce)').matches

  function spawnAmbient(count, factory) {
    if (prefersReduced()) return
    const batch = []
    for (let i = 0; i < count; i++) {
      batch.push({ id: nextId++, ambient: true, ...factory(i) })
    }
    particles.value.push(...batch)
  }

  function burst(count, factory, ttlMs) {
    if (prefersReduced()) return
    const batch = []
    for (let i = 0; i < count; i++) {
      batch.push({ id: nextId++, ambient: false, ...factory(i) })
    }
    particles.value.push(...batch)

    const timer = setTimeout(() => {
      const ids = new Set(batch.map((p) => p.id))
      particles.value = particles.value.filter((p) => !ids.has(p.id))
    }, ttlMs)
    timers.push(timer)
  }

  function clear() {
    timers.forEach(clearTimeout)
    timers = []
    particles.value = []
  }

  const instance = getCurrentInstance()
  if (instance) {
    onBeforeUnmount(clear)
  }

  return { particles, spawnAmbient, burst, clear }
}
