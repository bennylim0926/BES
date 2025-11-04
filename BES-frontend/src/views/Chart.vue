<template>
    <div class="p-8 bg-transparent">
      <!-- Histogram -->
      <div class="fixed bottom-0 left-0 w-full px-6 pb-6 bg-transparent">
        <TransitionGroup
          name="bar"
          tag="div"
          class="flex items-end gap-4 min-h-[500px] justify-center"
        >
          <div
            v-for="(item, idx) in smokeParticipants"
            :key="item.name"
            class="flex flex-col items-center transition-all duration-500 ease-in-out"
          >
            <!-- Label (name + score) above -->
            <div
              class="text-2xl font-anton text-white px-3 py-1 rounded"
              :class="colors[idx % colors.length]"
            >
              {{ item.name }}
            </div>
  
            <!-- Image (stays fixed vertically) -->
            <img
              v-if="imageMap[item.name]"
              :src="imageMap[item.name]"
              alt="icon"
              class="w-48 h-auto transition-transform duration-500"
            />
  
            <!-- Bar -->
            <div
              class="relative w-full bg-gray-200/30  rounded-t transition-all duration-500 ease-in-out flex items-center justify-center"
              :style="{ height: item.score * 60 + 'px' }"
            >
              <!-- Centered Score inside -->
              <span 
                v-if="item.score != 0"
                class="absolute font-anton inset-0 flex items-center justify-center text-white font-bold"
                :style="{ fontSize: `${item.score * 10 + 20}px` }"
              >
                {{ item.score }}
              </span>
            </div>
          </div>
        </TransitionGroup>
      </div>
    </div>
  </template>
  
<script setup>
  import { getImage, getSmokeList } from '@/utils/api'
import { createClient, subscribeToChannel } from '@/utils/websocket'
import { onMounted, ref, watch } from 'vue'
const imageMap = ref({})
const smokeParticipants = ref([])
const colors = ref(['bg-red-500', 'bg-blue-500','bg-gray-800','bg-gray-800','bg-gray-800','bg-gray-800','bg-gray-800','bg-gray-800'])

const updateList = (msg)=>{
    console.log(msg.battlers)
    smokeParticipants.value = {}
    smokeParticipants.value = msg.battlers
}

watch(
  smokeParticipants,
  async (newPlayers) => {
    for (const player of newPlayers) {
      if (!imageMap.value[player.name]) {
        imageMap.value[player.name] = await getImage(`${player.name}.png`)
      }
    }
  },
  { deep: true, immediate: true } // also run on first load
)
onMounted(async ()=>{
    subscribeToChannel(createClient(), "/topic/battle/smoke", (msg) => updateList(msg))
    const smoke = await getSmokeList()
    smokeParticipants.value = smoke.list
})
  </script>
  
  <style scoped>
  .bar-move {
    transition: transform 0.5s ease;
  }
  </style>