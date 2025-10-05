import { defineStore } from "pinia";
import { useRouter } from 'vue-router'
import { whoami } from "./api";



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
            this.isAuthenticated = false
        }
    },
    getters:{
        isLoggedIn: (state) => state.isAuthenticated,
        currentUser: (state)=> state.user
    }
})

export const checkAuthStatus = async (acceptedRoles)=>{
    // if not authenticated, redirect to login
    // if not authorised, redirect to 403 apge
    const router = useRouter()
    const res = await whoami()
    if(!res.authenticated){
        router.push({
            name: "Login"
        })
        return false
    }else{
        if(!acceptedRoles.includes(res.username)){
            router.push({
                name: "Forbidden"
            })
            return false
        }
    }
    return true
}