<script setup>
defineProps({
  show:         { type: Boolean, default: false },
  title:        { type: String,  default: 'Confirm' },
  message:      { type: String,  default: '' },
  confirmLabel: { type: String,  default: 'Confirm' },
  variant:      { type: String,  default: 'danger' }
})
const emit = defineEmits(['confirm', 'cancel'])
</script>

<template>
  <Teleport to="body">
    <div v-if="show" class="confirm-backdrop" @click.self="emit('cancel')">
      <div class="confirm-modal" role="dialog" aria-modal="true">
        <div class="confirm-header">
          <span class="type-label">{{ title }}</span>
        </div>
        <div class="confirm-body">
          <p class="type-body text-content-secondary">{{ message }}</p>
        </div>
        <div class="confirm-footer">
          <button class="btn-ghost" @click="emit('cancel')">Cancel</button>
          <button
            :class="variant === 'danger' ? 'btn-danger' : 'btn-warning'"
            @click="emit('confirm')"
          >{{ confirmLabel }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm-backdrop {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  padding: 16px;
}
.confirm-modal {
  background: var(--color-surface-800, #1a1a1a);
  border: 1px solid rgba(255,255,255,0.1);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  width: 100%; max-width: 440px;
  padding: 24px;
  display: flex; flex-direction: column; gap: 16px;
}
.confirm-header { border-bottom: 1px solid rgba(255,255,255,0.07); padding-bottom: 12px; }
.confirm-footer { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; }
.btn-ghost {
  background: none; border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.55); padding: 6px 16px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
.btn-danger {
  background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4);
  color: #f87171; padding: 6px 16px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
.btn-warning {
  background: rgba(245,158,11,0.15); border: 1px solid rgba(245,158,11,0.4);
  color: #fbbf24; padding: 6px 16px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
</style>
