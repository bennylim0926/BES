<script setup>
import { getBattleJudges, getCurrentBattlePair } from '@/utils/api';
import { checkAuthStatus } from '@/utils/auth';
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket';
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';

let client = createClient()
const subscribedTopics = new Set();
const rightName = ref("")
const leftName = ref("")

const leftScore = ref(0)
const rightScore = ref(0)

const battleJudges = ref([])

const color = ref(["blue", "red", "grey"]);

const updateBattlePair = (msg)=>{
    rightName.value = msg.right;
    rightScore.value = msg.rightScore;
    leftName.value = msg.left;
    leftScore.value = msg.leftScore;
}

const updateBattleJudge = (msg) => {
  battleJudges.value = msg;

  battleJudges.value.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`;
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic);
      subscribeToChannel(createClient(), topic, (msg) => updateJudgeVote(msg));
    }
  });
};

const judgeDecision = computed(() => {
  const judges = battleJudges.value?.judges ?? [];
  return judges
    .map(j => {
      const colorName =
        j.vote === -1 ? "grey" : color.value[j.vote] ?? "grey";
      return `${j.name}(${colorName})`;
    })
    .join(", ");
});

const updateScore = (msg) => {
    rightScore.value = msg.right
    leftScore.value = msg.left
}

const updateJudgeVote = (msg) => {  
    const updatedJudges = battleJudges.value.judges.map(j =>
        j.id === msg.judge ? { ...j, vote: msg.vote } : j
    );
    battleJudges.value = {
        ...battleJudges.value,
        judges: updatedJudges
    };
};

watch(battleJudges, (newVal)=>{
    newVal.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`;
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic);
      subscribeToChannel(createClient(), topic, (msg) => updateJudgeVote(msg));
    }
  });
},{deep : true})

onMounted(async ()=>{
    document.body.style.background = "transparent";
    const appRoot = document.getElementById("app");
    if (appRoot) appRoot.style.background = "transparent";
    battleJudges.value = await getBattleJudges()
    const res = await getCurrentBattlePair()
    updateBattlePair(res)
    subscribeToChannel(createClient(), "/topic/battle/battle-pair", (msg)=> updateBattlePair(msg))
    subscribeToChannel(createClient(), "/topic/battle/score", (msg)=> updateScore(msg))
    subscribeToChannel(createClient(), "/topic/battle/judges", (msg)=> updateBattleJudge(msg))
})

onBeforeUnmount(() => {
  deactivateClient(client.value)
})

onUnmounted(() => {
  document.body.style.background = "";
  const appRoot = document.getElementById("app");
  if (appRoot) appRoot.style.background = "";
});
</script>


<template>
  <div class="flex justify-center">
  <div
    class="flex justify-center items-center gap-8 mb-8 
           bg-white/80 backdrop-blur-sm px-8 py-4 rounded-2xl shadow-lg"
  >
    <div
      v-for="(j, index) in battleJudges?.judges || []"
      :key="index"
      class="flex flex-col items-center"
    >
      <!-- Judge Name -->
      <div class="font-bold text-2xl text-gray-900">
        {{ j.name }}
      </div>

      <!-- Colored Indicator -->
      <div
        class="w-8 h-2 rounded-full mt-1"
        :class="{
          'bg-blue-500': j.vote === 1,
          'bg-red-500': j.vote === 0,
          'bg-gray-400': j.vote === -1 || j.vote === null
        }"
      ></div>
    </div>
  </div>
</div>

  <!-- The rest of your existing bottom section -->
  <div class="font-bold text-gray-900 text-3xl">
    <div class="relative min-h-[20vh]">
      <!-- Bottom Left -->
      <div class="fixed bottom-8 left-6 bg-red-500 text-white p-3 rounded-xl shadow-lg">
        {{ leftName }} ({{ leftScore }})
      </div>

      <!-- Bottom Right -->
      <div class="fixed bottom-8 right-6 bg-blue-500 text-white p-3 rounded-xl shadow-lg">
        {{ rightName }} ({{ rightScore }})
      </div>
    </div>
  </div>
</template>

