import { ref, computed } from "vue";
import { useAuthStore } from "@/utils/auth";

export function useDropdowns(){
    const authStore = useAuthStore()
    // Reactive: updates automatically when the active event changes in the store
    const selectedEvent = computed(() => authStore.activeEvent?.name || localStorage.getItem("selectedEvent") || "")
    const selectedGenre = ref(null)
    const selectedJudge = ref(null)

    const initialiseDropdown = () => {
        selectedGenre.value = localStorage.getItem("selectedGenre") || "All"
    }
    return {selectedEvent, selectedGenre, selectedJudge, initialiseDropdown}
}
