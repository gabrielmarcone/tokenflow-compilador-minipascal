package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exporta a construcao SLR(1) em dois formatos:
 *   - relatorio em texto: gramatica numerada, simbolos, anulaveis, FIRST,
 *     FOLLOW, estados LR(0) com itens e transicoes, tabela ACTION/GOTO e
 *     conflitos (serve para a documentacao e para conferir a construcao);
 *   - CSV separado por ";" com a tabela ACTION/GOTO (abre direto no Excel).
 *
 * A saida nao tem data nem nada que mude entre execucoes: a mesma gramatica
 * sempre gera os mesmos arquivos.
 */
public final class ExportadorTabela {

    private static final String NL = "\n";

    private ExportadorTabela() {
    }

    // Relatorio em texto, secao por secao, na ordem em que a construcao acontece.
    public static String relatorio(AnaliseSLR analise, String titulo) {
        Gramatica g = analise.getGramatica();
        ConjuntosFirstFollow conj = analise.getConjuntos();
        ColecaoLR0 colecao = analise.getColecao();
        StringBuilder sb = new StringBuilder();

        sb.append("TABELAS SLR(1) - ").append(titulo).append(NL);

        secao(sb, "Gramática (" + (g.getProducoes().size() - 1)
                + " produções; a 0 é a produção inicial aumentada)");
        // numeros alinhados a direita
        int largura = String.valueOf(g.getProducoes().size() - 1).length();
        for (Producao p : g.getProducoes()) {
            sb.append(String.format("%" + largura + "d  ", p.getNumero())).append(p).append(NL);
        }

        secao(sb, "Símbolos");
        sb.append("Terminais (").append(g.getTerminais().size() - 1).append(", mais o $): ")
                .append(juntar(g.getTerminais())).append(NL);
        sb.append("Não terminais (").append(g.getNaoTerminais().size()).append("): ")
                .append(juntar(g.getNaoTerminais())).append(NL);

        secao(sb, "Não terminais anuláveis (derivam ε)");
        List<String> anulaveis = g.ordenar(conj.getAnulaveis());
        // S' nunca e anulavel de fato; fica fora da lista
        anulaveis.remove(g.getSimboloInicialAumentado());
        sb.append(anulaveis.isEmpty() ? "nenhum" : juntar(anulaveis)).append(NL);

        // Largura do maior nome de nao terminal, para alinhar FIRST e FOLLOW.
        int largNT = 0;
        for (String n : g.getNaoTerminais()) {
            largNT = Math.max(largNT, n.length());
        }
        secao(sb, "FIRST");
        for (String n : g.getNaoTerminais()) {
            sb.append(preencher(n, largNT)).append("  { ")
                    .append(juntar(g.ordenar(conj.first(n)))).append(" }").append(NL);
        }
        secao(sb, "FOLLOW");
        for (String n : g.getNaoTerminais()) {
            sb.append(preencher(n, largNT)).append("  { ")
                    .append(juntar(g.ordenar(conj.follow(n)))).append(" }").append(NL);
        }

        secao(sb, "Estados LR(0) (" + colecao.tamanho() + ")");
        for (EstadoLR0 estado : colecao.getEstados()) {
            sb.append("I").append(estado.getNumero()).append(NL);
            for (ItemLR0 item : estado.getItens()) {
                sb.append("    ").append(item.descrever(g)).append(NL);
            }
            Map<String, Integer> trans = colecao.transicoesDe(estado.getNumero());
            if (!trans.isEmpty()) {
                List<String> partes = new ArrayList<>();
                for (String s : g.ordenar(trans.keySet())) {
                    partes.add(s + " -> I" + trans.get(s));
                }
                sb.append("    transições: ").append(String.join(", ", partes)).append(NL);
            }
        }

        secao(sb, "Tabela ACTION / GOTO (sN = empilhar e ir ao estado N; rN = reduzir pela produção N; acc = aceitar)");
        sb.append(tabelaAlinhada(analise));

        secao(sb, "Conflitos");
        if (analise.getConflitos().isEmpty()) {
            sb.append("nenhum: a gramática é SLR(1).").append(NL);
        } else {
            for (ConflitoSLR c : analise.getConflitos()) {
                sb.append(c).append(NL);
            }
        }
        return sb.toString();
    }

    // A mesma tabela em CSV (;), um campo por celula.
    public static String csv(AnaliseSLR analise) {
        List<List<String>> linhas = matriz(analise);
        StringBuilder sb = new StringBuilder();
        for (List<String> linha : linhas) {
            List<String> campos = new ArrayList<>();
            for (String campo : linha) {
                campos.add(campoCsv(campo));
            }
            sb.append(String.join(";", campos)).append(NL);
        }
        return sb.toString();
    }

    // Grava <nomeBase>_tabela_slr.txt e, se nao houver conflitos, <nomeBase>_tabela_slr.csv.
    public static List<Path> gravar(AnaliseSLR analise, String titulo, Path pasta, String nomeBase)
            throws IOException {
        List<Path> gravados = new ArrayList<>();
        Path relatorio = pasta.resolve(nomeBase + "_tabela_slr.txt");
        Files.write(relatorio, relatorio(analise, titulo).getBytes(StandardCharsets.UTF_8));
        gravados.add(relatorio);
        if (!analise.temConflitos()) {
            Path csv = pasta.resolve(nomeBase + "_tabela_slr.csv");
            Files.write(csv, csv(analise).getBytes(StandardCharsets.UTF_8));
            gravados.add(csv);
        }
        return gravados;
    }

    // Cabecalho + uma linha por estado. Celula com conflito mostra as acoes separadas por "/".
    private static List<List<String>> matriz(AnaliseSLR analise) {
        Gramatica g = analise.getGramatica();
        List<List<String>> linhas = new ArrayList<>();
        List<String> cabecalho = new ArrayList<>();
        cabecalho.add("estado");
        cabecalho.addAll(g.getTerminais());
        cabecalho.addAll(g.getNaoTerminais());
        linhas.add(cabecalho);
        for (int estado = 0; estado < analise.getColecao().tamanho(); estado++) {
            List<String> linha = new ArrayList<>();
            linha.add(String.valueOf(estado));
            for (String t : g.getTerminais()) {
                Set<Acao> celula = analise.acoes(estado, t);
                List<String> partes = new ArrayList<>();
                for (Acao a : celula) {
                    partes.add(a.toString());
                }
                linha.add(String.join("/", partes));
            }
            for (String n : g.getNaoTerminais()) {
                int destino = analise.getColecao().transicao(estado, n);
                linha.add(destino < 0 ? "" : String.valueOf(destino));
            }
            linhas.add(linha);
        }
        return linhas;
    }

    // Tabela em texto com colunas da largura do maior valor de cada uma.
    private static String tabelaAlinhada(AnaliseSLR analise) {
        List<List<String>> linhas = matriz(analise);
        int colunas = linhas.get(0).size();
        int[] larguras = new int[colunas];
        for (List<String> linha : linhas) {
            for (int c = 0; c < colunas; c++) {
                larguras[c] = Math.max(larguras[c], linha.get(c).length());
            }
        }
        int terminais = analise.getGramatica().getTerminais().size();
        StringBuilder sb = new StringBuilder();
        for (List<String> linha : linhas) {
            StringBuilder l = new StringBuilder();
            for (int c = 0; c < colunas; c++) {
                // "||" separa ACTION (terminais) de GOTO (nao terminais)
                l.append(c == 0 ? "" : (c == terminais + 1 ? " || " : " | "));
                l.append(preencher(linha.get(c), larguras[c]));
            }
            sb.append(semEspacosNoFim(l.toString())).append(NL);
        }
        return sb.toString();
    }

    private static void secao(StringBuilder sb, String nome) {
        sb.append(NL).append("== ").append(nome).append(" ==").append(NL);
    }

    private static String juntar(Collection<String> simbolos) {
        return String.join(" ", simbolos);
    }

    private static String preencher(String texto, int largura) {
        StringBuilder sb = new StringBuilder(texto);
        while (sb.length() < largura) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String semEspacosNoFim(String texto) {
        int fim = texto.length();
        while (fim > 0 && texto.charAt(fim - 1) == ' ') {
            fim--;
        }
        return texto.substring(0, fim);
    }

    // Campo com ";", aspas ou quebra de linha vai entre aspas, com aspas duplicadas.
    static String campoCsv(String campo) {
        if (campo.contains(";") || campo.contains("\"") || campo.contains("\n") || campo.contains("\r")) {
            return "\"" + campo.replace("\"", "\"\"") + "\"";
        }
        return campo;
    }
}
