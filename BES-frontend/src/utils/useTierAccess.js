import { computed } from 'vue'
import { useAuthStore } from './auth'

export function useTierAccess() {
  const authStore = useAuthStore()

  const tier = computed(() => authStore.user?.tier ?? null)

  const battleEnabled = computed(() => {
    const authorities = (authStore.user?.role ?? []).map(r => r.authority)
    if (authorities.includes('ROLE_ADMIN')) return true
    if (authorities.includes('ROLE_ORGANISER')) return tier.value === 'MAX'
    return authStore.activeEventBattleEnabled
  })

  const isProUser = computed(() => tier.value === 'PRO')
  const isMaxUser = computed(() => tier.value === 'MAX')

  return { tier, battleEnabled, isProUser, isMaxUser }
}
