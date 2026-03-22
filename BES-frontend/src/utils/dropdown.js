import { ref } from "vue";
import { getActiveEvent } from "@/utils/auth";

export function useDropdowns(){
    const selectedEvent = ref(null)
    const selectedGenre = ref(null)
    const selectedJudge = ref(null)

    const iintialiseDropdown = ()=>{
        const active = getActiveEvent()
        selectedEvent.value = active?.name || localStorage.getItem("selectedEvent") || ""
        selectedGenre.value = localStorage.getItem("selectedGenre") || "All"
    }
    return {selectedEvent, selectedGenre, selectedJudge, iintialiseDropdown}
}
