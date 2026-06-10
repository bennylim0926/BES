<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const refCode = route.query.ref || ''
const participantName = route.query.name || ''

const qrImageUrl = ref('')
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  if (!refCode) {
    error.value = 'No reference code provided.'
    loading.value = false
    return
  }
  try {
    const res = await fetch(`/api/v1/results/qr?ref=${encodeURIComponent(refCode)}`, {
      credentials: 'include'
    })
    if (res.ok) {
      const blob = await res.blob()
      qrImageUrl.value = URL.createObjectURL(blob)
    } else {
      error.value = 'Could not generate QR code. You may not have permission.'
    }
  } catch (_e) {
    error.value = 'Network error. Please try again.'
  }
  loading.value = false
})

const print = () => window.print()
</script>

<template>
  <div class="min-h-screen flex flex-col items-center justify-center p-8 print:p-4 relative bg-surface-950">

    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <div class="relative z-10 w-full max-w-lg flex flex-col items-center">

      <!-- Loading — role=status announces async state -->
      <div v-if="loading" class="flex flex-col items-center gap-3" role="status">
        <i class="pi pi-spin pi-spinner text-4xl text-content-muted" aria-hidden="true"></i>
        <p class="type-body text-content-muted">Loading...</p>
      </div>

      <!-- Error — role=alert announces the failure -->
      <div v-else-if="error" role="alert" class="semantic-chip-error flex items-start gap-3 p-4 w-full max-w-md">
        <div class="w-2 h-2 rounded-full bg-red-400 flex-shrink-0 mt-0.5" style="box-shadow: 0 0 6px rgba(239,68,68,0.8)" aria-hidden="true"></div>
        <p class="type-body text-content-secondary">{{ error }}</p>
      </div>

      <!-- QR Card -->
      <div v-else class="flex flex-col items-center gap-6 w-full">
        <!-- BES branding -->
        <div class="flex items-center gap-2.5">
          <div class="glow-dot"></div>
          <span class="type-body tracking-[0.12em]">BES</span>
        </div>

        <!-- QR code image -->
        <div class="card-hover p-6 relative">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>
          <img
            :src="qrImageUrl"
            alt="Results QR Code"
            class="w-64 h-64 block"
          />
        </div>

        <!-- Participant info -->
        <div class="text-center">
          <div class="type-page-title">{{ participantName }}</div>
          <p class="type-label text-content-muted mt-1">{{ refCode }}</p>
        </div>

        <!-- Instructions -->
        <div class="para-chip px-5 py-4 text-center w-full max-w-sm">
          <p class="type-label text-content-muted">Scan this QR code to view your scores and feedback</p>
          <p class="type-label text-content-muted mt-1 opacity-60">Results are released by the organiser after all auditions</p>
        </div>

        <!-- Print button (hidden in print) -->
        <button
          @click="print"
          class="print:hidden px-5 py-2.5 min-h-[44px] bg-accent text-surface-900 type-body transition-all duration-200 flex items-center gap-2"
          style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)"
        >
          <i class="pi pi-print" aria-hidden="true"></i>
          Print QR code
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
@media print {
  body { background: white; }
}
</style>
