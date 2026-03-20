import { createApp } from 'vue'
import App from './App.vue'
import router from './router/';
import { createPinia } from 'pinia';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';

import '@/assets/main.css';
import 'primeicons/primeicons.css';

const app = createApp(App);

app.use(createPinia());
app.use(PrimeVue, {
    theme: {
        preset: Aura,
        options: {
            darkModeSelector: 'none', /* Forcing light mode for now since we built a premium light theme */
            cssLayer: {
                name: 'primevue',
                order: 'tailwind-base, primevue, tailwind-utilities'
            }
        }
    }
});

app.use(router).mount('#app');
