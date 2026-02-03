<template>
  <div class="detalhes-container">
    <button @click="$router.push('/operadoras')" class="btn-voltar">← Voltar</button>

    <div v-if="loading" class="loading">Carregando ficha técnica... 📂</div>
    
    <div v-else-if="operadora">
      <div class="card-topo">
        <h1>{{ operadora.razao_social }}</h1>
        <div class="grid-info">
          
          <p><strong>CNPJ:</strong> {{ formatarCNPJ(operadora.cnpj) }}</p>
          <p><strong>Registro ANS:</strong> {{ operadora.registro_ans }}</p>
          <p><strong>Modalidade:</strong> {{ operadora.modalidade }}</p>
          
          <p><strong>Telefone:</strong> {{ formatarTelefone(operadora.telefone) }}</p>
          
        </div>
      </div>

      <h3>Histórico de Despesas Reportadas</h3>
      <div class="tabela-card">
        <table v-if="historico.length > 0">
          <thead>
            <tr>
              <th>Ano</th>
              <th>Trimestre</th>
              <th>Valor Reportado</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in historico" :key="index">
              <td>{{ item.ano }}</td>
              <td>{{ item.trimestre }}</td>
              <td class="valor">{{ formatarMoeda(item.valor_despesa) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="vazio">Nenhuma despesa registrada para esta operadora.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const loading = ref(true)
const operadora = ref(null)
const historico = ref([])

// Formatação de CNPJ
const formatarCNPJ = (cnpj) => {
  if (!cnpj) return '-'
  const v = cnpj.toString().replace(/\D/g, '')
  return v.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, "$1.$2.$3/$4-$5")
}

// --- NOVO: Formatação de Telefone (Fixo ou Celular) ---
const formatarTelefone = (tel) => {
  if (!tel) return '-'
  const v = tel.toString().replace(/\D/g, '')
  
  // Se tiver 11 dígitos (Celular com DDD)
  if (v.length === 11) {
    return v.replace(/^(\d{2})(\d{5})(\d{4})/, "($1) $2-$3")
  } 
  // Se tiver 10 dígitos (Fixo com DDD)
  else if (v.length === 10) {
    return v.replace(/^(\d{2})(\d{4})(\d{4})/, "($1) $2-$3")
  }
  else if (v.length === 8) {
      return v.replace(/^(\d{4})(\d{4})/, " $1-$2")
    }
  
  return tel // Se não bater o tamanho, retorna original
}
// -----------------------------------------------------

const formatarMoeda = (valor) => {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor)
}

onMounted(async () => {
  const registro = route.params.registro
  try {
    const res = await axios.get(`http://127.0.0.1:8000/operadoras/${registro}/detalhes`)
    operadora.value = res.data.cadastro
    historico.value = res.data.historico_despesas
  } catch (error) {
    alert("Erro ao carregar detalhes.")
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.detalhes-container { max-width: 900px; margin: 0 auto; }
.btn-voltar { background: none; border: 1px solid #ccc; padding: 5px 15px; cursor: pointer; border-radius: 4px; margin-bottom: 20px; }
.card-topo { background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); margin-bottom: 30px; }
.grid-info { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 20px; }
.tabela-card { background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 15px; text-align: left; border-bottom: 1px solid #eee; }
.valor { font-family: monospace; font-weight: bold; color: #2c3e50; }
.vazio { padding: 20px; text-align: center; color: #777; }
</style>