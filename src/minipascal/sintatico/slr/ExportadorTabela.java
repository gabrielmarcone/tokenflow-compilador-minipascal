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
 * Exporta a construção SLR(1) em dois formatos:
 *   - relatório em texto: gramática numerada, símbolos, anuláveis, FIRST,
 *     FOLLOW, estados LR(0) com itens e transições, tabela ACTION/GOTO e
 *     conflitos (serve para a documentação e para conferir a construção);
 *   - CSV separado por ";" com a tabela ACTION/GOTO (abre direto no Excel).
 *
 * A saída não tem data nem nada que mude entre execuções: a mesma gramática
 * sempre gera os mesmos arquivos.
 */
public final class ExportadorTabela {

    private static final String NL = "\n";

    private ExportadorTabela() {
    }

    public static String relatorio(AnaliseSLR analise, String titulo) {
        Gramatica g = analise.getGramatica();
        ConjuntosFirstFollow conj = analise.getConjuntos();
        ColecaoLR0 colecao = analise.getColecao();
        StringBuilder sb = new StringBuilder();

        sb.append("TABELAS SLR(1) - ").append(titulo).append(NL);

        secao(sb, "Gramática (" + (g.getProducoes().size() - 1)
                + " produções; a 0 é a produção inicial aumentada)");
        int largura = String.valueOf(g.getProducoes().size() - 1).length();
        for (Producao p : g.getProducoes()) {
            sb.append(String.format("%" + largura + "d  ", p.getNumero())).append(p).append(NL);
        }

        secao(sb, "Símbolos");
        sb.append("Terminais (").append(g.getTerminais().size()).append("): ")
                .append(juntar(g.getTerminais())).append(NL);
        sb.append("Não terminais (").append(g.getNaoTerminais().size()).append("): ")
                .append(juntar(g.getNaoTerminais())).append(NL);

        secao(sb, "Não terminais anuláveis (derivam ε)");
        List<String> anulaveis = g.ordenar(conj.getAnulaveis());
        anulaveis.remove(g.getSimboloInicialAumentado());
        sb.append(anulaveis.isEmpty() ? "nenhum" : juntar(anulaveis)).append(NL);

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

    // Grava <nomeBase>_tabela_slr.txt e, se não houver conflitos, <nomeBase>_tabela_slr.csv.
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

    // Cabeçalho + uma linha por estado. Célula com conflito mostra as ações separadas por "/".
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
                // "||" separa ACTION (terminais) de GOTO (não terminais)
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
