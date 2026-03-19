<script setup>
import leftHand from '@/assets/lefthand.png'
import rightHand from '@/assets/righthand.png'
import tie from '@/assets/no.png'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { battleJudgeVote, getBattleJudges } from '@/utils/api'
import { computed, onMounted, ref } from 'vue'

const active = ref(null)
const confirmed = ref(null)
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
    confirmed.value = side
    setTimeout(() => { confirmed.value = null }, 1800)
  } else {
    active.value = side
  }
}

onMounted(async () => {
  battleJudges.value = await getBattleJudges()
})
</script>

<template>
  <div class="flex flex-col h-screen bg-surface-900">

    <!-- Judge selector bar — sits above panels via z-index -->
    <div class="relative z-20 flex-shrink-0 flex items-center justify-center px-6 py-3 bg-surface-900/90 backdrop-blur-sm border-b border-white/10">
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
    <div class="flex flex-1 overflow-hidden">
      <!-- Left panel -->
      <div
        class="flex-1 flex items-center justify-center cursor-pointer transition-all duration-300 select-none"
        :class="[
          active === 1
            ? 'bg-blue-500'
            : 'bg-blue-800 brightness-75 hover:brightness-90'
        ]"
        @click="handleClick(1)"
      >
        <div class="flex flex-col items-center gap-4">
          <img :src="leftHand" alt="Left" class="w-48 h-48 md:w-64 md:h-64 rotate-[315deg] drop-shadow-2xl" />
          <span class="text-white text-sm font-bold uppercase tracking-widest transition-all duration-200 text-center px-4"
            :class="(active === 1 || confirmed === 1) ? 'opacity-100' : 'opacity-0'"
          >
            <template v-if="confirmed === 1">✓ Vote Confirmed</template>
            <template v-else-if="active === 1">Tap again to confirm</template>
          </span>
        </div>
      </div>

      <!-- Tie panel -->
      <div
        class="flex-[0.6] flex items-center justify-center cursor-pointer transition-all duration-300 select-none"
        :class="[
          active === -1
            ? 'bg-surface-500'
            : 'bg-surface-700 brightness-75 hover:brightness-90'
        ]"
        @click="handleClick(-1)"
      >
        <div class="flex flex-col items-center gap-4">
          <img :src="tie" alt="Tie" class="w-32 h-32 md:w-44 md:h-44 drop-shadow-2xl" />
          <span class="text-white text-sm font-bold uppercase tracking-widest transition-all duration-200 text-center px-2"
            :class="(active === -1 || confirmed === -1) ? 'opacity-100' : 'opacity-0'"
          >
            <template v-if="confirmed === -1">✓ Vote Confirmed</template>
            <template v-else-if="active === -1">Tap again to confirm</template>
          </span>
        </div>
      </div>

      <!-- Right panel -->
      <div
        class="flex-1 flex items-center justify-center cursor-pointer transition-all duration-300 select-none"
        :class="[
          active === 0
            ? 'bg-red-500'
            : 'bg-red-800 brightness-75 hover:brightness-90'
        ]"
        @click="handleClick(0)"
      >
        <div class="flex flex-col items-center gap-4">
          <img :src="rightHand" alt="Right" class="w-48 h-48 md:w-64 md:h-64 rotate-45 drop-shadow-2xl" />
          <span class="text-white text-sm font-bold uppercase tracking-widest transition-all duration-200 text-center px-4"
            :class="(active === 0 || confirmed === 0) ? 'opacity-100' : 'opacity-0'"
          >
            <template v-if="confirmed === 0">✓ Vote Confirmed</template>
            <template v-else-if="active === 0">Tap again to confirm</template>
          </span>
        </div>
      </div>
    </div>

  </div>
</template>
