import { createRouter, createWebHistory } from "vue-router";
import MainMenu from "@/views/MainMenu.vue";
import Event from "@/views/Events.vue"
import EventDetails from "@/views/EventDetails.vue";
import AuditionNumber from "@/views/AuditionNumber.vue";
import UpdateEventDetails from "@/views/UpdateEventDetails.vue";
import AuditionList from "@/views/AuditionList.vue";
import Score from "@/views/Score.vue";
import Login from "@/views/Login.vue";
import ForbiddenPage from "@/components/ForbiddenPage.vue";
import AuditionDisplay from "@/views/AuditionDisplay.vue";
import BattleOverlay from "@/views/BattleOverlay.vue";
import BattleJudge from "@/views/BattleJudge.vue";
import BattleControl from "@/views/BattleControl.vue";
import Chart from "@/views/Chart.vue";
import AdminPage from "@/views/AdminPage.vue";
import EventSelector from "@/views/EventSelector.vue";
import Results from "@/views/Results.vue";
import ResultsQR from "@/views/ResultsQR.vue";
import CrewFormation from "@/views/CrewFormation.vue";
import AuditionAdjust from "@/views/AuditionAdjust.vue";
import BracketVisualization from "@/views/BracketVisualization.vue";
import TokenAuth from "@/views/TokenAuth.vue";
import JudgeSessionView from "@/views/JudgeSessionView.vue";
import EmceeSessionView from "@/views/EmceeSessionView.vue";
import HelperSessionView from "@/views/HelperSessionView.vue";
import { whoami } from "@/utils/api";
import { getActiveEvent, useAuthStore } from "@/utils/auth";
import { resolveBattleEnabled } from "@/utils/useTierAccess";

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
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER'] }
    },
    {
        path: '/events/:eventName',
        name: 'Event Details',
        component: EventDetails,
        props: route =>({
            eventName: route.params.eventName,
            folderID: route.query.folderID
        }),
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_HELPER'] }
    },
    {
        path: '/event/audition-number',
        name: 'Audition Number',
        component: AuditionNumber,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_HELPER'] }
    },
    {
        path: '/event/update-event-details',
        name: 'Update Event Details',
        component: UpdateEventDetails,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER'], requiresEvent: true }
    },
    {
        path: '/event/audition-list',
        name: 'Audition List',
        component: AuditionList,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE', 'ROLE_JUDGE'], requiresEvent: true }
    },
    {
        path: '/event/score',
        name: 'Score',
        component: Score,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_EMCEE', 'ROLE_ORGANISER', 'ROLE_HELPER'], requiresEvent: true }
    },
    {
        path: '/login',
        name: 'Login',
        component: Login
    },
    {
        path: '/403',
        name: 'Forbidden',
        component: ForbiddenPage
    },
    {
        path: '/audition/display',
        name: 'AuditionDisplay',
        component: AuditionDisplay
    },
    {
        path: '/battle/overlay',
        name: "StreamOverlay",
        component: BattleOverlay
    },
    {
        path: '/battle/judge',
        name: "Battle Judge",
        component: BattleJudge,
        meta: { allowedRoles: ['ROLE_JUDGE', 'ROLE_ADMIN'] }
    },
    {
        path: '/battle/control',
        name: "Battle Control",
        component: BattleControl,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE'], requiresEvent: true }
    },
    {
        path: '/battle/chart',
        name: "Smoke",
        component: Chart
    },
    {
        path: '/admin',
        name: "Admin Page",
        component: AdminPage,
        meta: { allowedRoles: ['ROLE_ADMIN'] }
    },
    {
        path: '/event/select',
        name: 'EventSelector',
        component: EventSelector
    },
    {
        path: '/results',
        name: 'Results',
        component: Results
    },
    {
        path: '/results-qr',
        name: 'ResultsQR',
        component: ResultsQR
    },
    {
        path: '/event/crew-formation',
        name: 'Crew Formation',
        component: CrewFormation,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER'], requiresEvent: true }
    },
    {
        path: '/battle/bracket',
        name: 'BracketVisualization',
        component: BracketVisualization
    },
    {
        path: '/event/audition-adjust',
        name: 'Audition Adjust',
        component: AuditionAdjust,
        meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER'], requiresEvent: true }
    },
    {
        path: '/auth/token',
        name: 'TokenAuth',
        component: TokenAuth
    },
    {
        path: '/auth/token/:role/:name?',
        name: 'TokenAuthRole',
        component: TokenAuth
    },
    {
        path: '/judge/session',
        name: 'JudgeSession',
        component: JudgeSessionView
    },
    {
        path: '/emcee/session',
        name: 'EmceeSession',
        component: EmceeSessionView
    },
    {
        path: '/helper/session',
        name: 'HelperSession',
        component: HelperSessionView
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

const PUBLIC_ROUTES = ['Login', 'Forbidden', 'StreamOverlay', 'Smoke', 'Results', 'ResultsQR', 'BracketVisualization', 'TokenAuth', 'TokenAuthRole', 'JudgeSession', 'EmceeSession', 'HelperSession', 'AuditionDisplay']
const BATTLE_ROUTES = ['Battle Control', 'Battle Judge', 'StreamOverlay', 'Smoke', 'BracketVisualization']

router.beforeEach(async (to) => {
    // Public routes short-circuit before any auth or tier checks
    // This ensures OBS displays (overlay/bracket/chart) work even in authenticated browser sessions
    if (PUBLIC_ROUTES.includes(to.name)) return true

    // Battle route guard: redirect authenticated PRO users away from all battle routes
    if (BATTLE_ROUTES.includes(to.name)) {
        try {
            const authStore = useAuthStore()
            let user = authStore.user
            if (!user) {
                user = await whoami()
                if (user?.authenticated) authStore.login(user)
            }
            if (user?.authenticated) {
                const battleEnabled = resolveBattleEnabled(user, authStore.activeEventBattleEnabled)
                if (!battleEnabled) return { name: 'Main' }
            }
        } catch { /* let existing checks handle it */ }
    }
    try {
        const authStore = useAuthStore()
        let user = authStore.user
        if (!user) {
            user = await whoami()
            if (user?.authenticated) authStore.login(user)
        }
        if (!user || !user.authenticated) return { name: 'Login' }

        const userRole = user.role?.[0]?.authority
        if (to.meta?.allowedRoles && !to.meta.allowedRoles.includes(userRole)) {
            return { name: 'Forbidden' }
        }

        // Session roles land on their hub, not the generic home
        if (userRole === 'ROLE_JUDGE' && to.name === 'Main') {
            return { name: 'JudgeSession' }
        }
        if (userRole === 'ROLE_EMCEE' && to.name === 'Main') {
            return { name: 'EmceeSession' }
        }
        if (userRole === 'ROLE_HELPER' && to.name === 'Main') {
            return { name: 'HelperSession' }
        }

        if (to.meta?.requiresEvent && !getActiveEvent()) {
            return { name: 'EventSelector', query: { redirect: to.fullPath } }
        }
    } catch {
        return { name: 'Login' }
    }
    return true
})

export default router
