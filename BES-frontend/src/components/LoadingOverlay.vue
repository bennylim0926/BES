<template>
  <div
    class="fixed inset-0 z-[100] flex items-center justify-center
           bg-surface-900/40 backdrop-blur-sm"
  >
    <!-- Floating card -->
    <div
      class="flex items-center gap-3.5 bg-white rounded-2xl
             px-7 py-5 shadow-2xl border border-surface-200/60
             animate-scale-in"
    >
      <!-- Circular spinner -->
      <svg
        class="w-5 h-5 text-primary-500 animate-spin flex-shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <circle
          cx="12" cy="12" r="10"
          stroke="currentColor"
          stroke-width="3"
          class="opacity-20"
        />
        <path
          d="M12 2a10 10 0 0 1 10 10"
          stroke="currentColor"
          stroke-width="3"
          stroke-linecap="round"
        />
      </svg>

      <span class="text-surface-700 font-semibold text-sm tracking-wide">
        <slot>Loading…</slot>
      </span>
    </div>
  </div>
</template>
