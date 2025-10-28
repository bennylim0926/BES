<template>
    <div class="flex justify-center items-center">
        <ReusableDropdown v-model="selectedJudge" labelId="Current Judge By:" :options="battleJudgesName"></ReusableDropdown>
    </div>
    <div class="flex h-screen">
      <!-- Left (Red) -->
      <div
        class="flex-1 flex items-center justify-center transition-all duration-500 cursor-pointer"
        :class="[
          active === 0
            ? 'bg-red-500 scale-105 brightness-110'
            : 'bg-red-700 scale-95 brightness-75'
        ]"
        @click="handleClick(0)"
      >
        <!-- <span class="text-white text-4xl font-bold">Left</span> -->
        <img :src="leftHand" alt="left Hand logo" class="w-80 h-80 rotate-315" />
      </div>
  
      <!-- Middle (Gray) -->
      <div
        class="flex-[0.8] flex items-center justify-center transition-all duration-500 cursor-pointer"
        :class="[
          active === -1
            ? 'bg-gray-400 scale-105 brightness-110'
            : 'bg-gray-600 scale-95 brightness-75'
        ]"
        @click="handleClick(-1)"
      >
        <!-- <span class="text-white text-4xl font-bold">Tie</span> -->
        <img :src="tie" alt="Tie logo" class="w-80 h-80" />
      </div>
  
      <!-- Right (Blue) -->
      <div
        class="flex-1 flex items-center justify-center transition-all duration-500 cursor-pointer"
        :class="[
          active === 1
            ? 'bg-blue-500 scale-105 brightness-110'
            : 'bg-blue-700 scale-95 brightness-75'
        ]"
        @click="handleClick(1)"
      >
        <!-- <span class="text-white text-4xl font-bold">Right</span> -->
        <img :src="rightHand" alt="right Hand logo" class="w-80 h-80 rotate-45" />
      </div>
    </div>
  </template>
  
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
  const battleJudgesName = computed(()=>{
    const judges = battleJudges.value?.judges ?? [];
    return judges
        .map(j => j.name)
  })
  
  async function handleClick(side) {
    // If user clicks same active button again
    if (active.value === side) {
        const judges = battleJudges.value?.judges ?? [];
        const id = judges
            .filter(j => j.name === selectedJudge.value)
            .map(j => j.id)
        await battleJudgeVote(id, side)
      active.value = null
    } else {
      active.value = side
    }
  }

  onMounted(async ()=>{
    battleJudges.value = await getBattleJudges()
  })
  </script>
  