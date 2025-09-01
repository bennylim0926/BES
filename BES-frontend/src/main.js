import { createApp } from 'vue'
import App from './App.vue'
import router from './router/';
import '@/assets/main.css';

const app = createApp(App);

// app.component('Splitter', Splitter)
// app.component('SplitterPanel', SplitterPanel)
// app.use(PrimeVue)
app.use(router).mount('#app')
// app.mount('#app')

