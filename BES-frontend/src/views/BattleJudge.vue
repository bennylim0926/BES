<script setup>
import leftHand from '@/assets/lefthand.png'
import rightHand from '@/assets/righthand.png'
import tie from '@/assets/no.png'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { battleJudgeVote, getBattleJudges } from '@/utils/api'
import { computed, onMounted, ref } from 'vue'

const active = ref(null)
const battleJudges = ref([])
const selectedJudge = ref("")
const battleJudgesName = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return judges.map(j => j.name)
})

async function handleClick(side) {
  if (active.value === side) {
    const judges = battleJudges.value?.judges ?? []
    const id = judges.filter(j => j.name === selectedJudge.value).map(j => j.id)
    await battleJudgeVote(id, side)
    active.value = null
  } else {
    active.value = side
  }
}

onMounted(async () => {
  battleJudges.value = await getBattleJudges()
})
</script>

<template>
  <div class="flex flex-col h-screen bg-surface-900 overflow-hidden">

    <!-- Judge selector bar -->
    <div class="flex-shrink-0 flex items-center justify-center px-6 py-3 bg-surface-900/90 backdrop-blur-sm border-b border-white/10">
      <div class="w-56">
        <ReusableDropdown
          v-model="selectedJudge"
          labelId="Voting as"
          :options="battleJudgesName"
          placeholder="Select judge…"
        />
      </div>
    </div>

    <!-- Voting panels -->
    <div class="flex flex-1">
      <!-- Left panel -->
      <div
        class="flex-1 flex items-center justify-center cursor-pointer transition-all duration-400 select-none"
        :class="[
          active === 1
            ? 'bg-blue-500 brightness-110'
            : 'bg-blue-800 brightness-75 hover:brightness-90'
        ]"
        @click="handleClick(1)"
      >
        <div class="flex flex-col items-center gap-4">
          <img :src="leftHand" alt="Left" class="w-48 h-48 md:w-64 md:h-64 rotate-[315deg] drop-shadow-2xl" />
          <span
            class="text-white/80 text-sm font-semibold uppercase tracking-widest transition-opacity"
            :class="active === 1 ? 'opacity-100' : 'opacity-0'"
          >
            Selected — tap again to confirm
          </span>
        </div>
      </div>

      <!-- Tie panel -->
      <div
        class="flex-[0.6] flex items-center justify-center cursor-pointer transition-all duration-400 select-none"
        :class="[
          active === -1
            ? 'bg-surface-500 brightness-110'
            : 'bg-surface-700 brightness-75 hover:brightness-90'
        ]"
        @click="handleClick(-1)"
      >
        <div class="flex flex-col items-center gap-4">
          <img :src="tie" alt="Tie" class="w-32 h-32 md:w-44 md:h-44 drop-shadow-2xl" />
          <span
            class="text-white/80 text-sm font-semibold uppercase tracking-widest transition-opacity"
            :class="active === -1 ? 'opacity-100' : 'opacity-0'"
          >
            Selected
          </span>
        </div>
      </div>

      <!-- Right panel -->
      <div
        class="flex-1 flex items-center justify-center cursor-pointer transition-all duration-400 select-none"
        :class="[
          active === 0
            ? 'bg-red-500 brightness-110'
            : 'bg-red-800 brightness-75 hover:brightness-90'
        ]"
        @click="handleClick(0)"
      >
        <div class="flex flex-col items-center gap-4">
          <img :src="rightHand" alt="Right" class="w-48 h-48 md:w-64 md:h-64 rotate-45 drop-shadow-2xl" />
          <span
            class="text-white/80 text-sm font-semibold uppercase tracking-widest transition-opacity"
            :class="active === 0 ? 'opacity-100' : 'opacity-0'"
          >
            Selected — tap again to confirm
          </span>
        </div>
      </div>
    </div>

  </div>
</template>
