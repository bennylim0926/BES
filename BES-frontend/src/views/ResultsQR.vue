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
  } catch (e) {
    error.value = 'Network error. Please try again.'
  }
  loading.value = false
})

const print = () => window.print()
</script>

<template>
  <div class="min-h-screen bg-white flex flex-col items-center justify-center p-8 print:p-4">

    <!-- Loading -->
    <div v-if="loading" class="flex flex-col items-center gap-3">
      <i class="pi pi-spin pi-spinner text-4xl text-surface-400"></i>
      <p class="text-sm text-surface-500">Generating QR code…</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="flex flex-col items-center gap-3 text-center">
      <i class="pi pi-exclamation-circle text-4xl text-red-400"></i>
      <p class="text-sm text-surface-600">{{ error }}</p>
    </div>

    <!-- QR Card -->
    <div v-else class="flex flex-col items-center gap-6 w-full max-w-xs">
      <!-- BES branding -->
      <div class="flex items-center gap-2">
        <div class="w-7 h-7 rounded-lg bg-primary-600 flex items-center justify-center">
          <i class="pi pi-star text-white text-xs"></i>
        </div>
        <span class="font-heading font-bold text-surface-700 text-sm">BES Results Portal</span>
      </div>

      <!-- QR code image -->
      <div class="rounded-2xl border-2 border-surface-200 p-4 bg-white shadow-sm">
        <img
          :src="qrImageUrl"
          alt="Results QR Code"
          class="w-64 h-64 block"
        />
      </div>

      <!-- Participant info -->
      <div class="text-center">
        <p class="font-heading font-bold text-surface-800 text-lg">{{ participantName }}</p>
        <p class="text-sm text-surface-500 mt-0.5 font-source tracking-widest">{{ refCode }}</p>
      </div>

      <!-- Instructions -->
      <div class="w-full rounded-xl border border-surface-200 bg-surface-50 px-4 py-3 text-center">
        <p class="text-xs text-surface-600 font-medium">Scan this QR code to view your scores and feedback</p>
        <p class="text-xs text-surface-400 mt-1">Results are released by the organiser after all auditions</p>
      </div>

      <!-- Print button (hidden in print) -->
      <button
        @click="print"
        class="print:hidden flex items-center gap-2 px-5 py-2.5 rounded-xl bg-surface-800 text-white text-sm font-semibold hover:bg-surface-700 transition-colors"
      >
        <i class="pi pi-print"></i>
        Print / Save
      </button>
    </div>
  </div>
</template>

<style scoped>
@media print {
  body { background: white; }
}
</style>
