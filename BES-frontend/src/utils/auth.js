import { defineStore } from "pinia";
import { useRouter } from 'vue-router'
import { whoami } from "./api";

const VERIFIED_KEY = 'bes_verified_events'
const ACTIVE_KEY = 'bes_active_event'

export const isEventVerified = (eventId) => {
    const stored = sessionStorage.getItem(VERIFIED_KEY)
    if (!stored) return false
    try {
        const ids = JSON.parse(stored)
        return Array.isArray(ids) && ids.includes(Number(eventId))
    } catch {
        return false
    }
}

export const markEventVerified = (eventId) => {
    const stored = sessionStorage.getItem(VERIFIED_KEY)
    let ids = []
    try { ids = stored ? JSON.parse(stored) : [] } catch { ids = [] }
    if (!ids.includes(Number(eventId))) ids.push(Number(eventId))
    sessionStorage.setItem(VERIFIED_KEY, JSON.stringify(ids))
}

export const setActiveEvent = (id, name) => {
    sessionStorage.setItem(ACTIVE_KEY, JSON.stringify({ id: Number(id), name }))
}

export const getActiveEvent = () => {
    const stored = sessionStorage.getItem(ACTIVE_KEY)
    if (!stored) return null
    try { return JSON.parse(stored) } catch { return null }
}

export const clearVerifiedEvents = () => {
    sessionStorage.removeItem(VERIFIED_KEY)
    sessionStorage.removeItem(ACTIVE_KEY)
}

export const useAuthStore = defineStore('auth',{
    state: ()=>({
        user: null,
        isAuthenticated: false
    }),
    actions:{
        login(userData){
            this.user = userData
            this.isAuthenticated = userData["authenticated"]
        },
        logout(){
            this.user = null;
            this.isAuthenticated = false;
            clearVerifiedEvents();
            localStorage.removeItem('selectedEvent');
            localStorage.removeItem('selectedRole');
            localStorage.removeItem('selectedGenre');
            localStorage.removeItem('currentJudge');
        }
    },
    getters:{
        isLoggedIn: (state) => state.isAuthenticated,
        currentUser: (state)=> state.user
    }
})

export const checkAuthStatus = async (acceptedRoles)=>{
    const router = useRouter()
    const res = await whoami()
    if(!res.authenticated){
        router.push({ name: "Login" })
        return false
    } else {
        const userRole = res.role?.[0]?.authority  // e.g. "ROLE_ADMIN"
        if(!acceptedRoles.includes(userRole)){
            router.push({ name: "Forbidden" })
            return false
        }
    }
    return true
}
