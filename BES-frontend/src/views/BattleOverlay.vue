<script setup>
import { getBattleJudges, getCurrentBattlePair, getImage } from '@/utils/api';
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket';
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';
import { useDelay } from '@/utils/utils';
import { useRoute } from 'vue-router'
import Chart from './Chart.vue';

const route = useRoute()

const imageLeft = ref(null)
const imageRight = ref(null)

let client = createClient()
const subscribedTopics = new Set();
const rightName = ref("")
const leftName = ref("")

const leftScore = ref(0)
const rightScore = ref(0)
const currentWinner = ref(-2)

const battleJudges = ref([])

const hideJudgeDecision = ref(true)

const leftWin = ref(false)
const rightWin = ref(false)

const leftReset = ref(false)
const rightReset = ref(false)

const isSmoke = computed(() => route.query.isSmoke === 'true')

const updateBattlePair = async (msg) => {
  // STEP 1: Animate current pair out (if visible)
  if (!hideJudgeDecision.value) {
    if (currentWinner.value === 0) {
      leftReset.value = true;
    } else if (currentWinner.value === 1) {
      rightReset.value = true;
    } else {
      leftReset.value = true;
      rightReset.value = true;
    }

    // Wait for the animation to finish
    await useDelay().wait(1000);

    // STEP 2: Hide both sides fully
    hideJudgeDecision.value = true;

    // Wait a few ms to ensure DOM reflow (prevents flicker)
    await useDelay().wait(50);

    // Reset reset flags
    leftReset.value = false;
    rightReset.value = false;
  }
  // STEP 3: Now update with the *new pair*
  leftWin.value = false;
  rightWin.value = false;
  currentWinner.value = -2;
  rightName.value = msg.right;
  rightScore.value = msg.rightScore;
  leftName.value = msg.left;
  leftScore.value = msg.leftScore;
  imageLeft.value = await getImage(`${leftName.value}.png`);
  imageRight.value = await getImage(`${rightName.value}.png`);
};

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

const updateScore = async (msg) => {
  // unhide the judge decision
  hideJudgeDecision.value = false
  rightScore.value = msg.right
  leftScore.value = msg.left
  await useDelay().wait(1000);
  currentWinner.value = msg.message
  if(msg.message === 0){
    leftWin.value = true
    rightWin.value = false
  }else if(msg.message === 1){
    leftWin.value = false
    rightWin.value = true
  }else if(msg.message === -1){
    leftWin.value = false
    rightWin.value = false
  }
  
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
    hideJudgeDecision.value = false
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
  {{ isSmoke }}
  <div class="flex justify-center">
  <div
    class="flex justify-center items-center gap-8 mb-8 
           bg-white/80 backdrop-blur-sm px-8 py-4 rounded-2xl shadow-lg"
    :class="{'slide-up': hideJudgeDecision, 'slide-down':!hideJudgeDecision}"
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
  <div v-if="!isSmoke" class="font-bold text-gray-900 text-7xl">
    <div class="relative min-h-[60vh]">
    <div 
      class="fixed bottom-15 left-12 flex flex-col items-center transition-transform duration-1000 z-10"
      :class="
      {'slide-left': rightWin, 
      'fade-out': rightWin, 
      'slide-right': leftWin, 
      'left-slide-in': hideJudgeDecision,
      'slide-left-out': leftReset}"
    >
      <div class="relative flex flex-col items-center">
        <!-- Big image behind name -->
        <img 
          :src="imageLeft" 
          alt="example" 
          class="absolute -bottom-30 max-w-none w-[80vh] h-auto object-contain -z-10"
        />
      <!-- Name + Score -->
      <div class="font-anton min-w-[55vh] bg-red-500 text-white p-3 rounded-xl shadow-lg text-center relative z-10">
        {{ leftName }}
         <!-- ({{ leftScore }}) -->
      </div>
  </div>
</div>
    <div 
      class="fixed bottom-15 right-12 flex flex-col items-center transition-transform duration-1000 z-10"
      :class="
      {'slide-left': rightWin, 
      'fade-out': leftWin,
      'slide-right': leftWin,
      'right-slide-in': hideJudgeDecision,
      'slide-right-out': rightReset}"
    >
    <div class="relative flex flex-col items-center">
    <!-- Big image behind name -->
    <img 
      :src="imageRight" 
      alt="example" 
      class="absolute -bottom-30 max-w-none w-[80vh] h-auto object-contain -z-10"
    />

    <!-- Name + Score -->
    <div class="font-anton min-w-[55vh] bg-blue-500 text-white p-3 rounded-xl shadow-lg text-center relative z-10">
      {{ rightName }} 
      <!-- ({{ rightScore }}) -->
    </div>
  </div>
</div>
    </div>
  </div>
  <div v-else>
    <Chart></Chart>
  </div>
  {{ hideJudgeDecision }}
</template>

<style>
@keyframes slideRight {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(450px); /* distance to move */
  }
}

@keyframes slideRightIn {
  0% {
    transform: translateX(600px);
  }
  100% {
    transform: translateX(0); /* distance to move */
  }
}

@keyframes slideLeft {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-450px); /* distance to move */
  }
}

@keyframes slideLeftIn {
  0% {
    transform: translateX(-600px);
  }
  100% {
    transform: translateX(0); /* distance to move */
  }
}

@keyframes slideUp {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(-150px); /* distance to move */
  }
}

@keyframes slideDown {
  0% {
    transform: translateY(-150px);
  }
  100% {
    transform: translateY(0px); /* distance to move */
  }
}

@keyframes fadeOut {
  0% {
    opacity: 100%
  }
  100% {
    opacity: 0%; 
  }
}
@keyframes ScaleUp {
  0% {
    transform: translateY(-150px);
  }
  100% {
    transform: translateY(0px); /* distance to move */
  }
}

.slide-right { animation: slideRight 1s ease forwards} 
.slide-left { animation: slideLeft 1s ease forwards }
.slide-up { animation: slideUp 1s ease forwards }
.slide-down { animation: slideDown 1s ease forwards }
.fade-out { animation: fadeOut 1s ease forwards }
.slide-right.fade-out { animation: slideRight 1s ease forwards, fadeOut 1s ease forwards;}
.slide-left.fade-out { animation: slideLeft 1s ease forwards, fadeOut 1s ease forwards;}
.left-slide-in { animation: slideLeftIn 1s ease forwards; }
.right-slide-in { animation: slideRightIn 1s ease forwards;}

@keyframes slideLeftOut {
  0% { transform: translateX(450px); opacity: 1; }
  100% { transform: translateX(-600px); opacity: 0; }
}

@keyframes slideRightOut {
  0% { transform: translateX(-450px); opacity: 1; }
  100% { transform: translateX(600px); opacity: 0; }
}

.slide-left-out,
.slide-right-out {
  animation-timing-function: ease-in;
}
.slide-left-out { animation: slideLeftOut 1s ease forwards; }
.slide-right-out { animation: slideRightOut 1s ease forwards; }
</style>

