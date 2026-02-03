-- ===================================================================
-- SCRIPT DE IMPORTAÇÃO DE DADOS (DML)
-- Execute este script no DBeaver após rodar o código Java
-- ===================================================================

-- 1. Importar as Operadoras (Arquivo bruto do CADOP)
-- Nota: O encoding esta para 'UTF8'
COPY tb_operadoras
FROM '/data/AUX_CSV/Relatorio_cadop.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ';', ENCODING 'UTF8', QUOTE '"');

-- 2. Importar Despesas Consolidadas (Gerado pelo Java)
COPY tb_despesas_consolidadas(cnpj_operadora, razao_social, trimestre, ano, valor_despesa, registro_ans, modalidade, uf)
FROM '/data/TEMP/consolidado_despesas.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ';', ENCODING 'UTF8');

-- 3. Importar Dados Agregados (Gerado pelo Java)
COPY tb_despesas_agregadas(razao_social, uf, total_despesas, media_trimestral, desvio_padrao)
FROM '/data/TEMP/despesas_agredadas.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ';', ENCODING 'UTF8');