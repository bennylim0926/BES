import { createRouter, createWebHistory } from "vue-router";
import MainMenu from "@/views/MainMenu.vue";
import Event from "@/views/Events.vue"
import EventDetails from "@/views/EventDetails.vue";

const routes = [
    // {
    //     path: '/main',
    //     name: 'Main',
    //     component: MainMenu
    // },
    {
        path: '/',
        name: 'Main',
        component: MainMenu
    },
    {
        path: '/events',
        name: 'Event',
        component: Event
    },
    {
        path: '/event/:eventName',
        name: 'Event Details',
        component: EventDetails,
        props: route =>({
            eventName: route.params.eventName,
            folderID: route.query.folderID
        })
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router