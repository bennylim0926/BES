import { ref } from "vue";
import { getAllJudges } from "./api";

export function useEventUtils(){
    const allJudges = ref([])
    const allEvents = ref([])
    const participants = ref([])
    
    const fetchAllJudges = async ()=>{
        allJudges.value = await getAllJudges()
    }
    return {allJudges, fetchAllJudges, allEvents, participants}
}