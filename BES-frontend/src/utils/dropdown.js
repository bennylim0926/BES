import { ref } from "vue";

export function useDropdowns(){
    const selectedEvent = ref(null)
    const selectedGenre = ref(null)
    const selectedJudge = ref(null)
    
    const iintialiseDropdown = ()=>{
        selectedEvent.value = localStorage.getItem("selectedEvent") || ""
        selectedGenre.value = localStorage.getItem("selectedGenre") || "All"
    }
    return {selectedEvent, selectedGenre, selectedJudge, iintialiseDropdown}
}