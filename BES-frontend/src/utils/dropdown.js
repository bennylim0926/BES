import { ref, computed } from "vue";
import { useAuthStore } from "@/utils/auth";

export function useDropdowns(){
    const authStore = useAuthStore()
    // Reactive: updates automatically when the active event changes in the store
    const selectedEvent = computed(() => authStore.activeEvent?.name || localStorage.getItem("selectedEvent") || "")
    const selectedCategory = ref(null)
    const selectedJudge = ref(null)

    const initialiseDropdown = () => {
        selectedCategory.value = localStorage.getItem("selectedCategory") || "All"
    }
    return {selectedEvent, selectedCategory, selectedJudge, initialiseDropdown}
}
