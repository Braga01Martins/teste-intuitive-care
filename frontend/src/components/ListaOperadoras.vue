<template>
  <div class="lista-container">
    <div class="header">
      <h1>Operadoras Ativas</h1>
      <input 
        v-model="termo" 
        @input="buscar" 
        placeholder="🔎 Buscar por Razão Social ou CNPJ..." 
        class="search-bar"
      />
    </div>

    <div class="tabela-card">
      <table>
        <thead>
          <tr>
            <th>Registro ANS</th>
            <th>CNPJ</th>
            <th>Razão Social</th>
            <th>Ação</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="op in operadoras" :key="op.registro_ans">
            <td>{{ op.registro_ans }}</td>
            
            <td class="cnpj">{{ formatarCNPJ(op.cnpj) }}</td>
            
            <td>{{ op.razao_social }}</td>
            <td>
              <router-link :to="'/operadora/' + op.registro_ans" class="btn-detalhes">
                Ver Detalhes
              </router-link>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="paginacao">
      <button @click="mudarPagina(page - 1)" :disabled="page === 1">Anterior</button>
      <span>Página {{ page }}</span>
      <button @click="mudarPagina(page + 1)">Próxima</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const operadoras = ref([])
const page = ref(1)
const termo = ref('')
let timeout = null

// --- FUNÇÃO NOVA DE FORMATAÇÃO ---
const formatarCNPJ = (cnpj) => {
  if (!cnpj) return '-'
  // Remove tudo que não for número (por segurança)
  const v = cnpj.toString().replace(/\D/g, '')
  
  // Aplica a máscara: 00.000.000/0000-00
  return v.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, "$1.$2.$3/$4-$5")
}
// ----------------------------------

const carregarDados = async () => {
  try {
    const res = await axios.get('http://127.0.0.1:8000/operadoras', {
      params: { page: page.value, limit: 10, search: termo.value }
    })
    operadoras.value = res.data.data
  } catch (error) {
    console.error("Erro API:", error)
  }
}

const mudarPagina = (novaPagina) => {
  if (novaPagina > 0) {
    page.value = novaPagina
    carregarDados()
  }
}

const buscar = () => {
  clearTimeout(timeout)
  timeout = setTimeout(() => {
    page.value = 1
    carregarDados()
  }, 500)
}

onMounted(() => {
  carregarDados()
})
</script>

<style scoped>
/* Mantem os estilos e adiciona um ajuste para o CNPJ não quebrar linha */
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.search-bar { padding: 10px; width: 300px; border: 1px solid #ddd; border-radius: 6px; }
.tabela-card { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); overflow: hidden; }
table { width: 100%; border-collapse: collapse; }
th { background: #f4f6f8; text-align: left; padding: 15px; font-weight: 600; color: #555; }
td { padding: 15px; border-bottom: 1px solid #eee; color: #333; }
.cnpj { white-space: nowrap; } 
.btn-detalhes { background: #42b983; color: white; padding: 6px 12px; text-decoration: none; border-radius: 4px; font-size: 0.9rem; }
.paginacao { margin-top: 20px; display: flex; justify-content: center; gap: 15px; align-items: center; }
button { padding: 8px 16px; background: white; border: 1px solid #ddd; cursor: pointer; border-radius: 4px; }
button:disabled { opacity: 0.5; cursor: not-allowed; }
</style>