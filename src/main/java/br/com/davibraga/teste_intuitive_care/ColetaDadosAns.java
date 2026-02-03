package br.com.davibraga.teste_intuitive_care;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ColetaDadosAns {

    // --- CONFIGURAÇÕES ---
    private static final String BASE_URL = "https://dadosabertos.ans.gov.br/FTP/PDA/demonstracoes_contabeis/2025/";
    private static final String[] FILE_NAMES = {"1T2025.zip", "2T2025.zip", "3T2025.zip"};
    
    // Pastas e Arquivos
    private static final String TEMP_FOLDER = "TEMP";
    private static final String AUX_FOLDER = "AUX_CSV";
    private static final String CADOP_FILE = "Relatorio_cadop.csv";
    
    // Outputs
    private static final String OUTPUT_CONSOLIDADO = "consolidado_despesas.csv";
    private static final String OUTPUT_AGREGADO = "despesas_agregadas.csv";

    // Filtros
    private static final String FILTER_TEXT = "Despesas com Eventos / Sinistros";
    

    // --- CLASSES DE DADOS ---

    // Dados imutáveis do CADOP (Join)
    private static class DadosCadop {
        String cnpjRaw; // Apenas números
        String razaoSocial;
        String registroAns;
        String modalidade;
        String uf;

        public DadosCadop(String cnpjRaw, String razaoSocial, String registroAns, String modalidade, String uf) {
            this.cnpjRaw = cnpjRaw;
            this.razaoSocial = razaoSocial;
            this.registroAns = registroAns;
            this.modalidade = modalidade;
            this.uf = uf;
        }
    }

    // Acumulador para Estatísticas (Agregação)
    private static class EstatisticaOperadora {
        String razaoSocial;
        String uf;
        double totalDespesas = 0.0;
        
        // Para Desvio Padrão (Algoritmo de Welford para variância incremental)
        long count = 0;
        double M2 = 0.0; // Soma dos quadrados das diferenças
        double mean = 0.0;

        // Para Média Trimestral
        Set<String> trimestresPresentes = new HashSet<>();

        public EstatisticaOperadora(String razaoSocial, String uf) {
            this.razaoSocial = razaoSocial;
            this.uf = uf;
        }

        public void adicionarDespesa(double valor, String trimestre) {
            this.totalDespesas += valor;
            this.trimestresPresentes.add(trimestre);

            // Welford's algorithm para Variância/StdDev on-the-fly
            this.count++;
            double delta = valor - this.mean;
            this.mean += delta / this.count;
            double delta2 = valor - this.mean;
            this.M2 += delta * delta2;
        }

        public double getMediaTrimestral() {
            if (trimestresPresentes.isEmpty()) return 0.0;
            
            // Média do total gasto por trimestre ativo
            return totalDespesas / trimestresPresentes.size();
        }

        public double getDesvioPadrao() {
            if (count < 2) return 0.0;
            return Math.sqrt(M2 / (count - 1));
        }
    }

    public static void main(String[] args) {
        try {
            long startTime = System.currentTimeMillis();
            
            String root = System.getProperty("user.dir");
            Path tempDir = Paths.get(root, TEMP_FOLDER);
            Path auxFile = Paths.get(root, AUX_FOLDER, CADOP_FILE);
            Path arquivoCadop = Paths.get("AUX_CSV/Relatorio_cadop.csv");
            
            Path pathConsolidado = tempDir.resolve(OUTPUT_CONSOLIDADO);
            Path pathAgregado = tempDir.resolve(OUTPUT_AGREGADO);

           /**Remove os espacos em branco null  do arquivo baixado de 
            * operadoras_de_plano_de_saude_ativas*/
         
            tratarArquivoCadop(arquivoCadop);
            
            // 1. Setup de Diretórios
            prepararDiretorio(tempDir);

            // 2. Carregar CADOP em Memória (Hash Join Setup)
            // Mapa Principal: Chave = REG_ANS (pois é o dado confiável no arquivo raw da ANS)
            // Mapa Secundário: Chave = CNPJ (para cumprir o requisito explícito de join por CNPJ)
            
            System.out.println("[ETAPA 1] Carregando tabela CADOP...");
            Map<String, DadosCadop> mapPorCnpj = carregarCadopPorCnpj(auxFile);
            Map<String, String> mapRegAnsParaCnpj = carregarRegAnsParaCnpj(auxFile); // Helper para linkar raw -> cnpj

            System.out.println(" > Operadoras indexadas por CNPJ: " + mapPorCnpj.size());

            // 3. Processamento Stream (Pipeline)
            // Mapa para agregação: Chave = "RazaoSocial|UF"
            Map<String, EstatisticaOperadora> mapaAgregacao = new HashMap<>();

            System.out.println("[ETAPA 2] Iniciando processamento e geração do consolidado...");
            
            try (BufferedWriter writerConsolidado = Files.newBufferedWriter(pathConsolidado, StandardCharsets.UTF_8)) {
                
                // Cabeçalho Consolidado Atualizado
                writerConsolidado.write("CNPJ;RazaoSocial;trimestre;Ano;ValorDespesas;RegistroANS;Modalidade;UF");
                writerConsolidado.newLine();

                for (String zipName : FILE_NAMES) {
                    processarZip(zipName, tempDir, writerConsolidado, mapPorCnpj, mapRegAnsParaCnpj, mapaAgregacao);
                }
            }

            // 4. Geração do Arquivo Agregado (Estatísticas)
            System.out.println("[ETAPA 3] Gerando arquivo de estatísticas agregadas...");
            gerarArquivoAgregado(pathAgregado, mapaAgregacao);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("Processo finalizado em " + (duration / 1000) + " segundos.");

        } catch (Exception e) {
            System.err.println("ERRO FATAL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- MÉTODOS DE LOAD (CADOP) ---

    private static Map<String, DadosCadop> carregarCadopPorCnpj(Path path) throws IOException {
        Map<String, DadosCadop> map = new HashMap<>();
        if (!Files.exists(path)) throw new FileNotFoundException("CADOP não encontrado: " + path);

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = br.readLine();
            if (line != null) {
                int iCnpj = findCol(line, "CNPJ");
                int iRazao = findCol(line, "RAZAO_SOCIAL");
                int iReg = findCol(line, "REGISTRO_OPERADORA");
                int iMod = findCol(line, "MODALIDADE"); // Pode não existir, verificar nome exato
                int iUf = findCol(line, "UF");

                while ((line = br.readLine()) != null) {
                    String[] p = line.split(";", -1);
                    if (p.length > iReg && p.length > iCnpj) {
                        String cnpjRaw = limparCnpj(getVal(p, iCnpj));
                        String razao = getVal(p, iRazao);
                        String reg = getVal(p, iReg);
                        String mod = (iMod != -1) ? getVal(p, iMod) : "N/A";
                        String uf = (iUf != -1) ? getVal(p, iUf) : "BR";

                        if (!cnpjRaw.isEmpty()) {
                            map.put(cnpjRaw, new DadosCadop(cnpjRaw, razao, reg, mod, uf));
                        }
                    }
                }
            }
        }
        return map;
    }

    // Helper  para mapear REG_ANS -> CNPJ (para fazer a ponte entre Raw Data e o Join Key)
    private static Map<String, String> carregarRegAnsParaCnpj(Path path) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = br.readLine();
            if(line != null) {
                int iReg = findCol(line, "REGISTRO_OPERADORA");
                int iCnpj = findCol(line, "CNPJ");
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(";", -1);
                    if (p.length > iReg && p.length > iCnpj) {
                        map.put(getVal(p, iReg), limparCnpj(getVal(p, iCnpj)));
                    }
                }
            }
        }
        return map;
    }

    // --- MÉTODOS DE PROCESSAMENTO (CORE) ---

    private static void processarZip(String fileName, Path tempDir, BufferedWriter writer,
                                     Map<String, DadosCadop> cadopMap, 
                                     Map<String, String> regToCnpjMap,
                                     Map<String, EstatisticaOperadora> aggMap) {
        
        String urlStr = BASE_URL + fileName;
        String rawName = fileName.replace(".zip", "");
        String trim = rawName.substring(0, 2);
        String ano = rawName.length() >= 6 ? rawName.substring(2, 6) : "2025";
        
        System.out.println(" > Baixando e processando: " + fileName);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            if (conn.getResponseCode() == 200) {
                try (ZipInputStream zis = new ZipInputStream(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".csv")) {
                            
                            BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8)); // ISO-8859-1 se der erro de acento
                            String header = reader.readLine();
                            
                            if (header != null) {
                                int iReg = findCol(header, "REG_ANS");
                                int iDesc = findCol(header, "DESCRICAO");
                                int iVal = findCol(header, "VL_SALDO_FINAL");

                                if (iReg != -1 && iDesc != -1 && iVal != -1) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        // 1. Filtro
                                        if (contemTexto(line, iDesc, FILTER_TEXT)) {
                                            String[] parts = line.split(";", -1);
                                            
                                            // 2. Extração de Dados
                                            String regAnsRaw = getVal(parts, iReg);
                                            String valStr = getVal(parts, iVal);
                                            
                                            if (!isNumeric(valStr)) continue;
                                            double valor = parseDouble(valStr);

                                            // 3. Lógica de JOIN (Complexa conforme requisito)
                                            // Requisito: Usar CNPJ como chave.
                                            // Passo A: Descobrir o CNPJ a partir do REG_ANS (dado que temos no arquivo)
                                            String chaveCnpj = regToCnpjMap.getOrDefault(regAnsRaw, "");
                                            
                                            // Passo B: Usar o CNPJ para buscar os dados complementares no Map Principal
                                            DadosCadop dados = cadopMap.get(chaveCnpj);

                                            // Dados finais para escrita
                                            String cnpjFinal = "";
                                            String razaoFinal = "null";
                                            String modalidade = "DESCONHECIDO";
                                            String uf = "DESCONHECIDO";
                                            String regAnsFinal = regAnsRaw;

                                            if (dados != null) {
                                                // Validação de CNPJ e Formatação
                                                if (isValidCNPJ(dados.cnpjRaw)) {
                                                    cnpjFinal = formatarCnpj(dados.cnpjRaw);
                                                }
                                                if (dados.razaoSocial != null && !dados.razaoSocial.isEmpty()) {
                                                    razaoFinal = dados.razaoSocial;
                                                }
                                                modalidade = dados.modalidade;
                                                uf = dados.uf;
                                                regAnsFinal = dados.registroAns; // Garante consistência
                                            } else {
                                                // REGISTROS SEM MATCH NO CADASTRO
                                                // Decisão de Trade-off: Mantemos o registro financeiro, mas marcamos como desconhecido.
                                                // Isso evita "sumir" com dinheiro nas demonstrações.
                                            }

                                            // 4. Escrita no CONSOLIDADO
                                            // Ordem: CNPJ;RazaoSocial;trimestre;Ano;ValorDespesas;RegistroANS;Modalidade;UF
                                            String linhaConsolidada = String.format("%s;%s;%s;%s;%.2f;%s;%s;%s",
                                                cnpjFinal, razaoFinal, trim, ano, valor, regAnsFinal, modalidade, uf
                                            );
                                            writer.write(linhaConsolidada.replace(",", ".")); // Padroniza ponto decimal
                                            writer.newLine();

                                            // 5. Atualização da AGREGAÇÃO (Memória)
                                            if (!razaoFinal.equals("null") && !uf.equals("DESCONHECIDO")) {
                                                String key = razaoFinal + "|" + uf;
                                                aggMap.putIfAbsent(key, new EstatisticaOperadora(razaoFinal, uf));
                                                aggMap.get(key).adicionarDespesa(valor, trim);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                System.err.println("Erro baixar " + fileName + ": " + conn.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- GERAÇÃO DO ARQUIVO AGREGADO (DESAFIO) ---

    private static void gerarArquivoAgregado(Path path, Map<String, EstatisticaOperadora> map) throws IOException {
        // 1. Converter Map para List para ordenar
        List<EstatisticaOperadora> lista = new ArrayList<>(map.values());

        // 2. Ordenação (Trade-off: Memória vs Disco - Escolhido Memória por volume baixo de operadoras)
        // Critério: Valor Total (Maior para Menor)
        lista.sort((o1, o2) -> Double.compare(o2.totalDespesas, o1.totalDespesas));

        // 3. Escrita
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write("RazaoSocial;UF;TotalDespesas;MediaDespesasPorTrimestre;DesvioPadraoDespesas");
            bw.newLine();

            DecimalFormat df = new DecimalFormat("#0.00", new DecimalFormatSymbols(Locale.US));

            for (EstatisticaOperadora op : lista) {
                String linha = String.format("%s;%s;%s;%s;%s",
                    op.razaoSocial,
                    op.uf,
                    df.format(op.totalDespesas),
                    df.format(op.getMediaTrimestral()),
                    df.format(op.getDesvioPadrao())
                );
                bw.write(linha);
                bw.newLine();
            }
        }
    }

    // --- UTILITÁRIOS ---
 // --- MÉTODOS DE VALIDAÇÃO DE CNPJ ---

 // 1. Arruma o CNPJ (Tira lixo e coloca zeros a esquerda)
 private static String padronizarCnpj(String cnpj) {
     // Remove tudo que não é número
     String apenasNumeros = cnpj.replaceAll("[^0-9]", "");
     
     // Se estiver vazio, retorna vazio
     if (apenasNumeros.isEmpty()) return "";

     // Se tiver menos de 14 dígitos (erro do Excel), preenche com zeros à esquerda
     while (apenasNumeros.length() < 14) {
         apenasNumeros = "0" + apenasNumeros;
     }
     
     // Se tiver mais de 14 (erro grave), corta
     if (apenasNumeros.length() > 14) {
         apenasNumeros = apenasNumeros.substring(0, 14);
     }

     return apenasNumeros;
 }

 // 2. Valida matemática (Dígitos Verificadores) - Algoritmo Padrão Receita Federal
 public static boolean isCnpjValido(String cnpj) {
     cnpj = padronizarCnpj(cnpj);
     if (cnpj.length() != 14 || cnpj.matches("(\\d)\\1{13}")) return false;

     try {
         int sm, i, r, num, peso;
         char dig13, dig14;

         // Calculo do 1o. Digito Verificador
         sm = 0;
         peso = 2;
         for (i = 11; i >= 0; i--) {
             num = (int) (cnpj.charAt(i) - 48);
             sm = sm + (num * peso);
             peso = peso + 1;
             if (peso == 10) peso = 2;
         }
         r = sm % 11;
         if ((r == 0) || (r == 1)) dig13 = '0';
         else dig13 = (char) ((11 - r) + 48);

         // Calculo do 2o. Digito Verificador
         sm = 0;
         peso = 2;
         for (i = 12; i >= 0; i--) {
             num = (int) (cnpj.charAt(i) - 48);
             sm = sm + (num * peso);
             peso = peso + 1;
             if (peso == 10) peso = 2;
         }
         r = sm % 11;
         if ((r == 0) || (r == 1)) dig14 = '0';
         else dig14 = (char) ((11 - r) + 48);

         return (dig13 == cnpj.charAt(12)) && (dig14 == cnpj.charAt(13));
     } catch (InputMismatchException e) {
         return false;
     }
 }
 // Método para ler, limpar e sobrescrever o arquivo Relatorio_cadop.csv original em UTF-8
    
    private static void tratarArquivoCadop(Path caminhoArquivo) throws IOException {
        System.out.println("Lendo e validando CNPJs no arquivo: " + caminhoArquivo);

        List<String> linhas = Files.readAllLines(caminhoArquivo, StandardCharsets.UTF_8);
        List<String> linhasTratadas = new ArrayList<>();

        // Pega cabeçalho
        String cabecalho = "";
        int numColunas = 0;
        if (!linhas.isEmpty()) {
            cabecalho = linhas.get(0).replace("\t", ";").replace(",", ";");
            // Força ponto e vírgula no cabeçalho
            linhasTratadas.add(cabecalho);
            numColunas = cabecalho.split(";").length;
        }

        int cnpjsCorrigidos = 0;
        int cnpjsInvalidos = 0;

        for (int i = 1; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            
            // Normaliza separadores
            if (linha.contains("\t")) linha = linha.replace("\t", ";");
            else if (linha.contains(",") && !linha.contains(";")) linha = linha.replace(",", ";");

            String[] colunas = linha.split(";", -1);
            List<String> colunasFinais = new ArrayList<>();

            // O CNPJ geralmente é a coluna 1 (índice 1) ou 0 dependendo do arquivo. 
            // No seu CSV enviado: Coluna 0 = Registro, Coluna 1 = CNPJ.
            
            for (int j = 0; j < numColunas; j++) {
                String valor = (j < colunas.length) ? colunas[j] : "";
                valor = valor.replace("\"", "").trim();
                if (valor.isEmpty()) valor = "vazio";

                // --- AQUI ENTRA A VALIDAÇÃO DO CNPJ (Coluna Index 1) ---
                if (j == 1) { 
                    String cnpjOriginal = valor;
                    String cnpjNovo = padronizarCnpj(cnpjOriginal); // Coloca os zeros
                    
                    // Valida a matemática
                    if (!isCnpjValido(cnpjNovo)) {
                        // DECISÃO DE PROJETO: Não paramos o processo, apenas avisamos e salvamos o formatado
                        System.out.println("⚠️ AVISO: CNPJ matematicamente inválido na linha " + (i+1) + ": " + cnpjNovo);
                        cnpjsInvalidos++;
                    } else if (!cnpjOriginal.equals(cnpjNovo)) {
                        cnpjsCorrigidos++;
                    }
                    valor = cnpjNovo; // Salva o CNPJ arrumadinho no arquivo
                }
                // -------------------------------------------------------

                colunasFinais.add(valor);
            }
            linhasTratadas.add(String.join(";", colunasFinais));
        }

        Files.write(caminhoArquivo, linhasTratadas, StandardCharsets.UTF_8);
        System.out.println("Processamento concluído!");
        System.out.println("CNPJs corrigidos (zeros adicionados): " + cnpjsCorrigidos);
        System.out.println("CNPJs matematicamente inválidos (mantidos): " + cnpjsInvalidos);
    }
    private static String getVal(String[] parts, int idx) {
        if (idx >= parts.length) return "";
        return parts[idx].replace("\"", "").trim();
    }

    private static int findCol(String header, String name) {
        String[] cols = header.split(header.contains(";") ? ";" : ",");
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].replace("\"", "").trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static boolean contemTexto(String line, int idx, String txt) {
        String[] p = line.split(";", -1);
        return p.length > idx && p[idx].toLowerCase().contains(txt.toLowerCase());
    }

    private static String limparCnpj(String raw) {
        return raw.replaceAll("[^0-9]", "");
    }

    private static String formatarCnpj(String c) {
        if (c == null) return "";
        //  garantir  que vai gravar no CSV apenas "12345678000199"
        return c.replaceAll("[^0-9]", "");
    }

    private static boolean isValidCNPJ(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return false;
        // Implementação simplificada para brevidade. 
        return !cnpj.matches("(\\d)\\1{13}"); 
    }
    
    private static boolean isNumeric(String str) { 
        return str != null && str.matches("-?\\d+(\\.\\d+)?(,\\d+)?");
    }
    
    private static double parseDouble(String val) {
        return Double.parseDouble(val.replace(".", "").replace(",", "."));
    }

    private static void prepararDiretorio(Path dir) throws IOException {
        if (Files.exists(dir) && !Files.isDirectory(dir)) Files.delete(dir);
        if (!Files.exists(dir)) Files.createDirectories(dir);
    }
}
