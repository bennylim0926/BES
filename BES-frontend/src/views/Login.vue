<script setup>
import ReusableButton from '@/components/ReusableButton.vue';
import { login } from '@/utils/api';
import { ref } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth';

const router = useRouter()
const authStore = useAuthStore();


// need sanitize and encode
const username = ref("")
const password = ref("")

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
}
const handleAccept = () => {
  showModal.value = false
}

const submitLogin = async(username, password) =>{
    const res = await login(username, password)
    if(res.status !== 200){
        openModal("Error", "Username or Password is wrong")
    }else{
        const data = await res.json()
        authStore.login(data)
        router.push({
            name: 'Main'
        });
    }
}
</script>

<template>
    <div class="flex flex-col h-screen justify-center items-center align-middle">
        <form >
            <h1 class="text-3xl font-bold mb-8">Login</h1>
            <!-- <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Username</label> -->
            <input
            
            placeholder="Username"
            v-model="username"
            class="mb-3 border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"></input>

            <!-- <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Password</label> -->
            <input
            placeholder="Password"
            type="password"
            v-model="password"
            class="mb-3 border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"></input>
            
        </form>
        <ReusableButton class="m-3" buttonName="Login" @onClick="submitLogin(username,password)"></ReusableButton>
    </div>
    <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    @accept="handleAccept"
    @close="()=>{showModal=false}"
    >
        <p>
        {{ modalMessage}}
        </p>
    </ActionDoneModal>
</template>