import { createRouter, createWebHistory } from "vue-router";
import MainMenu from "@/views/MainMenu.vue";
import Event from "@/views/Events.vue"
import EventDetails from "@/views/EventDetails.vue";
import AuditionNumber from "@/views/AuditionNumber.vue";
import UpdateEventDetails from "@/views/UpdateEventDetails.vue";
import AuditionList from "@/views/AuditionList.vue";
import Score from "@/views/Score.vue";

const routes = [
    {
        path: '/',
        name: 'Main',
        component: MainMenu
    },
    {
        path: '/events',
        name: 'Event',
        component: Event,
        children: [
            { path: ':eventName', component: EventDetails } // /events/something
          ]
    },
    {
        path: '/events/:eventName',
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
    },
    {
        path: '/event/audition-list',
        name: 'Audition List',
        component: AuditionList
    },
    {
        path: '/event/score',
        name: 'Score',
        component: Score
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router