import { createRouter, createWebHistory } from "vue-router";
import MainMenu from "@/views/MainMenu.vue";
import Event from "@/views/Events.vue"
import EventDetails from "@/views/EventDetails.vue";
import AuditionNumber from "@/views/AuditionNumber.vue";
import UpdateEventDetails from "@/views/UpdateEventDetails.vue";

const routes = [
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
    },
    {
        path: '/event/audition-number',
        name: 'Audition Number',
        component: AuditionNumber
    },
    {
        path: '/event/update-event-details',
        name: 'Update Event Details',
        component: UpdateEventDetails
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router