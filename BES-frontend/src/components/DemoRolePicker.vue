<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click="$emit('back')">
      <div class="role-picker" @click.stop>
        <h2 class="picker-title">Try Kyrove as&hellip;</h2>

        <div class="role-cards">
          <button
            v-for="role in roles"
            :key="role.key"
            class="role-card"
            @click="$emit('select', role.key)"
          >
            <span class="role-icon">{{ role.icon }}</span>
            <span class="role-name">{{ role.label }}</span>
            <span class="role-desc type-prose-sm">{{ role.description }}</span>
          </button>
        </div>

        <p class="type-prose-sm picker-hint">Tap outside to go back</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
defineEmits(['select', 'back'])

const roles = [
  {
    key: 'EMCEE',
    icon: '\u{1F3A4}',
    label: 'Emcee',
    description: 'Run audition rounds, view scoreboard, announce results'
  },
  {
    key: 'JUDGE',
    icon: '\u{2696}\u{FE0F}',
    label: 'Judge',
    description: 'Score participants, submit feedback, use the keypad'
  },
  {
    key: 'HELPER',
    icon: '\u{1F6CE}\u{FE0F}',
    label: 'Helper',
    description: 'Check-in participants, verify details, see QR codes'
  }
]
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 1rem;
}

.role-picker {
  max-width: 680px;
  width: 90vw;
  padding: 2rem;
  background: #1a1a1a;
  border: 1px solid rgba(255,255,255,0.1);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}

.picker-title {
  font-family: var(--font-sans);
  font-size: 20px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #fff;
  text-align: center;
  margin-bottom: 1.5rem;
}

.role-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

@media (max-width: 600px) {
  .role-cards {
    grid-template-columns: 1fr;
  }
}

.role-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem 1rem;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  color: #fff;
}

.role-card:hover {
  background: rgba(255,255,255,0.08);
  border-color: var(--accent-muted);
}

.role-icon {
  font-size: 32px;
}

.role-name {
  font-family: var(--font-sans);
  font-size: 16px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--accent-color);
}

.role-desc {
  text-align: center;
  color: rgba(255,255,255,0.45);
}

.picker-hint {
  margin-top: 1.5rem;
  color: rgba(255,255,255,0.3);
  text-align: center;
}
</style>
