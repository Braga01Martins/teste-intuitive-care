import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'

// Componentes (Vamos criar eles no próximo passo!)
import Dashboard from './components/Dashboard.vue'
import ListaOperadoras from './components/ListaOperadoras.vue'
import DetalhesOperadora from './components/DetalhesOperadora.vue'

// Rotas do Site
const routes = [
    { path: '/', component: Dashboard },
    { path: '/operadoras', component: ListaOperadoras },
    { path: '/operadora/:registro', component: DetalhesOperadora }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

const app = createApp(App)
app.use(createPinia()) // Ativa o Pinia
app.use(router)        // Ativa as Rotas
app.mount('#app')