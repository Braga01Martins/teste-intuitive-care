-- ===================================================================
-- SCRIPT DE IMPORTAÇÃO DE DADOS (DML)
-- Execute este script no DBeaver após rodar o código Java
-- ===================================================================

-- 1. Importar Relatorio_cadop.csv (Arquivo bruto do CADOP)
-- Nota: O encoding esta para 'UTF8'
COPY tb_operadoras
FROM '/data/AUX_CSV/Relatorio_cadop.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ';', ENCODING 'UTF8', QUOTE '"');

-- 2. Importar consolidado_despesas (Gerado pelo Java)
COPY tb_consolidado_despesas(cnpj_operadora, razao_social, trimestre, ano, valor_despesa, registro_ans, modalidade, uf)
FROM '/data/TEMP/consolidado_despesas.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ';', ENCODING 'UTF8', QUOTE '"');

-- 3. Importar despesas_agregadas (Gerado pelo Java)
COPY tb_despesas_agregadas(razao_social, uf, total_despesas, media_trimestral, desvio_padrao)
FROM '/data/TEMP/despesas_agregadas.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ';', ENCODING 'UTF8', QUOTE '"');