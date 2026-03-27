import { ref } from 'vue'

/**
 * useScrollReveal — IntersectionObserver composable for scroll-triggered reveals.
 *
 * Attaches to a container element and observes all direct/nested `.reveal` children.
 * Adds `is-visible` class when element enters the viewport.
 * Respects `prefers-reduced-motion` by revealing immediately when motion is reduced.
 *
 * Usage:
 *   const { revealRef } = useScrollReveal()
 *   <div :ref="revealRef"><div class="reveal reveal-delay-1">...</div></div>
 */
export function useScrollReveal() {
  const revealRef = ref(null)

  const setupObserver = (el) => {
    if (!el) return

    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const items = el.querySelectorAll('.reveal')

    if (prefersReduced) {
      items.forEach(item => item.classList.add('is-visible'))
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.1 }
    )

    items.forEach(item => observer.observe(item))
  }

  const revealRefCallback = (el) => {
    revealRef.value = el
    if (el) setupObserver(el)
  }

  return { revealRef: revealRefCallback }
}
