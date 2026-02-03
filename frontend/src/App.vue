<template>
  <div id="app">
    <nav>
      <div class="logo">intuitive<span>care</span></div>
      <div class="links">
        <router-link to="/">Dashboard</router-link>
        <router-link to="/operadoras">Operadoras</router-link>
      </div>
    </nav>
    <router-view class="conteudo" />
  </div>
</template>

<style>
/* Estilo global limpo e profissional */
body { margin: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8f9fa; }
nav { 
  background: white; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}
.logo { font-weight: bold; font-size: 1.2rem; color: #2c3e50; }
.logo span { color: #42b983; }
.links a { text-decoration: none; color: #6c757d; margin-left: 20px; font-weight: 500; }
.links a.router-link-active { color: #42b983; border-bottom: 2px solid #42b983; padding-bottom: 5px; }
.conteudo { padding: 40px; max-width: 1200px; margin: 0 auto; }
</style>