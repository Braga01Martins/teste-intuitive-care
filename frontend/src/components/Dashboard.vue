<template>
  <div class="dashboard-container">
    <h1>Dashboard Financeiro</h1>
    <p class="subtitulo">Top 10 Estados com maiores despesas trimestrais</p>

    <div v-if="loading" class="loading">Carregando gráfico... 📊</div>

    <div v-else class="grafico-card">
      <Bar :data="chartData" :options="chartOptions" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { Bar } from 'vue-chartjs'
import { Chart as ChartJS, Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale } from 'chart.js'

// Registra os componentes obrigatórios do Chart.js
ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale)

const loading = ref(true)
const chartData = ref({ labels: [], datasets: [] })

const chartOptions = {
  responsive: true,
  plugins: {
    legend: { display: false }, // Esconde a legenda pois já tem título
    tooltip: {
      callbacks: {
        label: (context) => {
          // Formata o valor para Reais (R$) no tooltip
          return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(context.raw)
        }
      }
    }
  }
}

const carregarDados = async () => {
  try {
    const res = await axios.get('http://127.0.0.1:8000/dashboard/despesas-por-uf')
    const dados = res.data

    // Separa os dados para o gráfico
    const estados = dados.map(item => item.uf || 'Indefinido')
    const valores = dados.map(item => item.total)

    chartData.value = {
      labels: estados,
      datasets: [
        {
          label: 'Total de Despesas',
          backgroundColor: '#42b983',
          data: valores,
          borderRadius: 5
        }
      ]
    }
    loading.value = false
  } catch (error) {
    console.error("Erro ao carregar gráfico:", error)
  }
}

onMounted(() => {
  carregarDados()
})
</script>

<style scoped>
.dashboard-container { text-align: center; }
.subtitulo { color: #666; margin-bottom: 30px; }
.grafico-card { 
  background: white; padding: 20px; border-radius: 8px; 
  box-shadow: 0 4px 12px rgba(0,0,0,0.1); 
  max-width: 800px; margin: 0 auto; height: 400px;
}
.loading { font-size: 1.2rem; color: #42b983; margin-top: 50px; }
</style>