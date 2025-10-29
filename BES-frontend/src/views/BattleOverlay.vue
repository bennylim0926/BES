<script setup>
import { getBattleJudges, getCurrentBattlePair, getImage } from '@/utils/api';
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket';
import { onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';

const imageLeft = ref(null)
const imageRight = ref(null)

let client = createClient()
const subscribedTopics = new Set();
const rightName = ref("")
const leftName = ref("")

const leftScore = ref(0)
const rightScore = ref(0)

const battleJudges = ref([])

const color = ref(["blue", "red", "grey"]);

const updateBattlePair = async (msg)=>{
  // animation here
  // hide the judge decision
  rightName.value = msg.right;
  rightScore.value = msg.rightScore;
  leftName.value = msg.left;
  leftScore.value = msg.leftScore;
  imageLeft.value = await getImage(`${leftName.value}.png`)
  imageRight.value = await getImage(`${rightName.value}.png`)
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

const updateScore = (msg) => {
  // unhide the judge decision
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
      <div class="font-anton font-bold text-4xl text-gray-900">
        {{ j.name }}
      </div>

      <!-- Colored Indicator -->
      <div
        class="w-20 h-6 rounded mt-1"
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
  <div class="font-bold text-gray-900 text-7xl">
    
    <div class="relative min-h-[60vh]">
      <!-- Bottom Left -->
      <img :src=imageLeft alt="example" class="fixed -left-30"></img>
      <div class="font-anton min-w-[55vh] fixed bottom-15 left-12 bg-red-500 text-white p-3 rounded-xl shadow-lg">
        <div class="flex items-center justify-center">
        {{ leftName }} ({{ leftScore }})
      </div>
      </div>

      <!-- Bottom Right -->
      <img :src=imageRight alt="example" class="fixed -right-30"></img>
      <div class="font-anton min-w-[55vh] fixed bottom-15 right-12 bg-blue-500 text-white p-3 rounded-xl shadow-lg">
        <div class="flex items-center justify-center">
        {{ rightName }} ({{ rightScore }})
      </div>
      </div>
    </div>
  </div>
</template>

