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
    console.log(msg)
    const updatedJudges = battleJudges.value.judges.map(j =>
        j.id === msg.judge ? { ...j, vote: msg.vote } : j
    );
    console.log(updatedJudges)
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
<div class="font-bold text-gray-900 text-3xl">
Right side: {{ rightName }}
Left side: {{ leftName }}
<br></br>

Right Score: {{ rightScore }}
Left Score: {{ leftScore }}
<br></br>

<!-- Vote: {{ battleJudges }} -->

<br></br>
{{ judgeDecision }}
</div>



</template>
