from flask import Flask, jsonify, request
from flask_cors import CORS
import psycopg2
from psycopg2.extras import RealDictCursor

app = Flask(__name__)
# Permite que o Vue.js acesse a API
CORS(app) 

# Configuração do Banco (Bate com seu docker-compose)
DB_CONFIG = {
    "dbname": "intuitive_care_db",
    "user": "postgres",
    "password": "password",
    "host": "localhost",
    "port": "5432"
}

def get_connection():
    try:
        return psycopg2.connect(**DB_CONFIG, cursor_factory=RealDictCursor)
    except Exception as e:
        print(f"Erro de conexão: {e}")
        return None

@app.route('/', methods=['GET'])
def home():
    return jsonify({"status": "API Flask Online 🚀"})

# --- ROTA 1: LISTAGEM PAGINADA COM BUSCA ---
@app.route('/operadoras', methods=['GET'])
def listar_operadoras():
    conn = get_connection()
    if not conn:
        return jsonify({"erro": "Erro ao conectar no banco"}), 500
    
    cur = conn.cursor()

    # Pega parâmetros da URL (ex: ?page=1&limit=10&search=Unimed)
    page = request.args.get('page', 1, type=int)
    limit = request.args.get('limit', 10, type=int)
    search = request.args.get('search', None)

    offset = (page - 1) * limit

    # Base da Query
    sql = "SELECT registro_ans, cnpj, razao_social, modalidade FROM tb_operadoras WHERE 1=1"
    params = []

    # Adiciona filtro se houver busca
    if search:
        sql += " AND (razao_social ILIKE %s OR cnpj ILIKE %s)"
        termo = f"%{search}%"
        params.extend([termo, termo])
    
    # Conta total
    count_sql = f"SELECT count(*) as total FROM ({sql}) as sub"
    cur.execute(count_sql, params)
    total_registros = cur.fetchone()['total']

    # Finaliza query
    sql += " ORDER BY razao_social ASC LIMIT %s OFFSET %s"
    params.extend([limit, offset])

    cur.execute(sql, params)
    dados = cur.fetchall()

    cur.close()
    conn.close()

    return jsonify({
        "data": dados,
        "total": total_registros,
        "page": page,
        "limit": limit,
        "total_pages": (total_registros // limit) + 1
    })

# --- ROTA 2: DADOS PARA O GRÁFICO ---
@app.route('/dashboard/despesas-por-uf', methods=['GET'])
def despesas_uf():
    conn = get_connection()
    cur = conn.cursor()
    
    cur.execute("""
        SELECT uf, SUM(valor_despesa) as total 
        FROM tb_consolidado_despesas 
        WHERE uf IS NOT NULL 
        GROUP BY uf 
        ORDER BY total DESC 
        LIMIT 10
    """)
    dados = cur.fetchall()
    cur.close()
    conn.close()
    return jsonify(dados)

# --- ROTA 3: DETALHES DA OPERADORA ---
@app.route('/operadoras/<registro_ans>/detalhes', methods=['GET'])
def detalhes_operadora(registro_ans):
    conn = get_connection()
    cur = conn.cursor()

    # 1. Cadastro
    cur.execute("SELECT * FROM tb_operadoras WHERE registro_ans = %s", (registro_ans,))
    operadora = cur.fetchone()

    if not operadora:
        return jsonify({"erro": "Operadora não encontrada"}), 404

    # 2. Histórico
    cur.execute("""
        SELECT ano, trimestre, valor_despesa 
        FROM tb_consolidado_despesas 
        WHERE registro_ans = %s 
        ORDER BY ano DESC, trimestre DESC
    """, (registro_ans,))
    despesas = cur.fetchall()

    cur.close()
    conn.close()

    return jsonify({
        "cadastro": operadora,
        "historico_despesas": despesas
    })

if __name__ == '__main__':
    app.run(debug=True, port=8000)