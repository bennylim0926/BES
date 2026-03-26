<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-surface-950 px-6">
    <div class="text-center max-w-md">
      <!-- Icon -->
      <div class="w-20 h-20 rounded-3xl bg-red-950 flex items-center justify-center mx-auto mb-6">
        <i class="pi pi-lock text-red-400 text-3xl"></i>
      </div>

      <!-- Error code -->
      <div class="text-7xl font-heading font-extrabold text-surface-700 mb-2 leading-none">403</div>

      <!-- Message -->
      <h1 class="font-heading font-extrabold text-2xl text-content-primary mb-2">Access Denied</h1>
      <p class="text-content-muted text-sm leading-relaxed mb-8">
        You don't have permission to view this page. Please contact your administrator if you believe this is a mistake.
      </p>

      <!-- Action -->
      <button
        @click="router.push('/')"
        class="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-primary-600 text-white
               text-sm font-semibold hover:bg-primary-700 shadow-sm transition-all duration-200 btn-glow"
      >
        <i class="pi pi-home text-xs"></i>
        Back to Home
      </button>
    </div>
  </div>
</template>
