<script setup>
import { computed, onMounted, ref } from "vue";
import { logout, whoami } from "./utils/api";
import { useAuthStore } from "./utils/auth";
import ActionDoneModal from "./views/ActionDoneModal.vue";
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
}

const handleAccept = () => {
  showModal.value = false
  logoutNow(isOpen.value)
  router.push({
    name: 'Login'
  });
}

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const authStore = useAuthStore();

const isOpen = ref(false);

const role = computed(()=>{
  return authStore.user ? authStore.user["role"][0]["authority"] : ''
})

const isAuthenticated = computed(()=>{
  return authStore.isAuthenticated
})

const logoutNow = async(isOpen)=>{
  isOpen = !isOpen
  await logout()
  authStore.logout()
}
onMounted( async () =>{
  const res = await whoami()
  authStore.login(res)
})
</script>

<template>
  <nav class="bg-white border-gray-200 dark:bg-gray-900">
    <div v-if="route.fullPath != '/battle/overlay' && route.fullPath != '/battle/judge'" class="max-w-screen-xl flex flex-wrap items-center justify-between mx-auto p-4">
      <!-- Logo -->
      <router-link to="/" class="text-xl font-bold text-orange-400 dark:text-white">
        BES
      </router-link>

      <!-- Mobile Hamburger -->
      <button
        @click="isOpen = !isOpen"
        type="button"
        class="inline-flex items-center p-2 w-10 h-10 justify-center text-sm text-gray-500 rounded-lg md:hidden hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
      >
        <span class="sr-only">Open main menu</span>
        <svg class="w-5 h-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 17 14">
          <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M1 1h15M1 7h15M1 13h15" />
        </svg>
      </button>

      <!-- Menu -->
      <div
        :class="[isOpen ? 'block' : 'hidden', 'w-full md:block md:w-auto']"
        id="navbar-default"
      >
        <ul
          class="font-medium flex flex-col p-4 md:p-0 mt-4 border border-gray-100 rounded-lg bg-gray-50 
                 md:flex-row md:space-x-8 md:mt-0 md:border-0 md:bg-white 
                 dark:bg-gray-800 md:dark:bg-gray-900 dark:border-gray-700"
        >
          <li>
            <router-link @click="isOpen = !isOpen" to="/"
            v-slot="{ isActive }">
                  <span :class="isActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Home
                  </span>
            </router-link>
          </li>
          <li v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'">
            <router-link @click="isOpen = !isOpen" to="/events"
             v-slot="{ isActive, isExactActive }">
                  <span :class="isActive || isExactActive? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Events
                  </span>
            </router-link>
          </li>
          <li v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'">
            <router-link @click="isOpen = !isOpen" to="/event/audition-number"
            v-slot="{ isActive }">
                  <span :class="isActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Audtion Number
                  </span>
            </router-link>
          </li>
          <li v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'">
            <router-link @click="isOpen = !isOpen" to="/event/update-event-details"
            v-slot="{ isActive }">
                  <span :class="isActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Participant Details
                  </span>
            </router-link>
          </li>
          <li v-if="role === 'ROLE_ADMIN' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'">
            <router-link @click="isOpen = !isOpen" to="/event/audition-list"
            v-slot="{ isActive }">
                  <span :class="isActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Audition List
                  </span>
            </router-link>
          </li>
          <li v-if="role === 'ROLE_ADMIN' || role === 'ROLE_EMCEE' || role === 'ROLE_ORGANISER'">
            <router-link @click="isOpen = !isOpen" to="/event/score"
            v-slot="{ isExactActive }">
                  <span :class="isExactActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Score
                  </span>
            </router-link>
          </li>
          <li v-if="role === 'ROLE_ADMIN'">
            <router-link @click="isOpen = !isOpen" to="/battle/overlay"
            v-slot="{ isExactActive }">
                  <span :class="isExactActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Battle Overlay
                  </span>
            </router-link>
          </li>
          <li v-if="isAuthenticated === false">
            <router-link @click="isOpen = !isOpen" to="/login"
            v-slot="{ isExactActive }">
                  <span :class="isExactActive ? 'text-orange-400' : 'text-gray-900 md:text-gray-900 dark:text-gray-100'"
                        class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Login
                  </span>
            </router-link>
          </li>
          
          <li v-if="isAuthenticated === true">
            <a @click="openModal('Warning','Are you sure you want to Logout?')">
                  <span class="block py-2 px-3 rounded-sm 
                            hover:bg-gray-100 md:hover:bg-transparent 
                            md:border-0 md:p-0">
                    Logout
                  </span>
                </a>
          </li>
        </ul>
      </div>
    </div>
  </nav>
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

  <router-view />
</template>
