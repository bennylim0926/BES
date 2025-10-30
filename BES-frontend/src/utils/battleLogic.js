import { ref, computed } from "vue"

export function useBattleLogic(){
    const rounds = ref({})
    const topSize = ref(localStorage.getItem("topSize" || 16))
    const sizes  = ref([7, 8, 16, 32])

    const roundSizes = computed(()=>{
        const arr = []
        let cur = Number(topSize.value);
        while(cur >= 2){
            arr.push(cur)
            cur /= 2;
        }
        return arr
    })

    const isSmoke = computed(()=>{
        if(Number(topSize.value) === 7){
            return true
        }return false
    })
    const standardBattleRound = () =>{
        roundSizes.value.forEach(size =>{
            const matches = [];
            for(let i = 0; i < size / 2; i++){
                matches.push([null,null,null])
            }
            rounds.value[`Top${size}`] = matches;
        })
        return rounds.value
    }

    const sevenToSmokeRound = () =>{
        return Array.from({ length: 8 }, () => ({
            name: null,
            score: 0
          }));
    }

    // const initRounds = () =>{
    //     rounds.value = {}
    //     if(Number(topSize.value) === 7){
    //         rounds.value = sevenToSmokeRound()
    //     }else{
    //         standardBattleRound()
    //     }
    // }
    return {rounds, topSize, roundSizes, isSmoke, 
        standardBattleRound, sevenToSmokeRound
    }
}