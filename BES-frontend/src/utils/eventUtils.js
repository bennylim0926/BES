import { ref } from "vue";
import { getEventJudges } from "./api";

export function useEventUtils(){
    const allJudges = ref([])
    const allEvents = ref([])
    const participants = ref([])

    const fetchAllJudges = async (eventName) => {
        allJudges.value = eventName ? await getEventJudges(eventName) : []
    }
    return {allJudges, fetchAllJudges, allEvents, participants}
}