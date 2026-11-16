package minipascal.semantico;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tabela de símbolos com escopos aninhados: uma tabela hash por escopo,
 * empilhadas (livro do Dragão, seção 2.7).
 *
 *   - abrirEscopo: empilha uma tabela nova (o bloco de um procedimento);
 *   - declarar: insere no escopo do topo; recusa um nome repetido nele;
 *   - buscar: procura do topo para a base, então o nome local esconde o de
 *     fora (um parâmetro x esconde uma variável global x);
 *   - fecharEscopo: desempilha, e os nomes locais deixam de ser visíveis.
 *
 * Escopos: 0 "externo" (read, write, writeln e o nome do programa, que ficam
 * fora do programa e podem ser redeclarados nele); 1 "global" (variáveis e
 * procedimentos do programa); 2 em diante, dentro dos procedimentos.
 *
 * Os nomes são comparados sem diferenciar maiúsculas de minúsculas, como no
 * Pascal e como as palavras reservadas do léxico: Soma e soma são o mesmo nome.
 *
 * Além da pilha, a tabela guarda o histórico de todos os símbolos declarados,
 * na ordem da declaração, para mostrar a tabela inteira depois da análise
 * (os escopos já fechados não estão mais na pilha).
 */
public final class TabelaSimbolos {

    public static final String ESCOPO_EXTERNO = "externo";
    public static final String ESCOPO_GLOBAL = "global";

    private static final class Escopo {
        final String nome;
        final Map<String, Simbolo> simbolos = new HashMap<>();

        Escopo(String nome) {
            this.nome = nome;
        }
    }

    private final List<Escopo> pilha = new ArrayList<>();
    private final List<Simbolo> historico = new ArrayList<>();

    // Começa com o escopo externo já aberto.
    public TabelaSimbolos() {
        pilha.add(new Escopo(ESCOPO_EXTERNO));
    }

    /**
     * Abre um escopo dentro do atual. O nome completo junta os nomes dos
     * procedimentos que o contêm ("soma.aux"); o global se chama "global".
     */
    public void abrirEscopo(String nome) {
        String pai = topo().nome;
        boolean dentroDeProcedimento = pilha.size() > 2;
        pilha.add(new Escopo(dentroDeProcedimento ? pai + "." + nome : nome));
    }

    public void fecharEscopo() {
        if (pilha.size() == 1) {
            throw new IllegalStateException("o escopo externo não pode ser fechado");
        }
        pilha.remove(pilha.size() - 1);
    }

    // Nível do escopo atual (0 = externo).
    public int nivelAtual() {
        return pilha.size() - 1;
    }

    public String escopoAtual() {
        return topo().nome;
    }

    /**
     * Declara no escopo atual.
     *
     * @return null se deu certo; se o nome já existe neste escopo, o símbolo que
     *         já estava (o novo não é inserido)
     */
    public Simbolo declarar(Simbolo simbolo) {
        Escopo escopo = topo();
        String chave = chave(simbolo.getNome());
        Simbolo existente = escopo.simbolos.get(chave);
        if (existente != null) {
            return existente;
        }
        simbolo.registrarEscopo(escopo.nome, nivelAtual());
        escopo.simbolos.put(chave, simbolo);
        historico.add(simbolo);
        return null;
    }

    // O símbolo visível com esse nome (do escopo mais interno para o externo), ou null.
    public Simbolo buscar(String nome) {
        String chave = chave(nome);
        for (int i = pilha.size() - 1; i >= 0; i--) {
            Simbolo s = pilha.get(i).simbolos.get(chave);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    // Só no escopo atual, ou null.
    public Simbolo buscarNoEscopoAtual(String nome) {
        return topo().simbolos.get(chave(nome));
    }

    // Todos os símbolos declarados, na ordem da declaração (inclusive os de escopos já fechados).
    public List<Simbolo> getHistorico() {
        return Collections.unmodifiableList(historico);
    }

    /**
     * A tabela inteira em texto, uma linha por símbolo, sem os nomes "não
     * declarados" (que só existem para evitar mensagens repetidas):
     *
     *   Escopo   Nível  Nome   Categoria  Tipo     Linha  Parâmetros
     *   global   1      x      variável   integer  2
     */
    public String listar() {
        String[] cabecalho = {"Escopo", "Nível", "Nome", "Categoria", "Tipo", "Linha", "Parâmetros"};
        List<String[]> linhas = new ArrayList<>();
        linhas.add(cabecalho);
        for (Simbolo s : historico) {
            if (s.getCategoria() == CategoriaSimbolo.NAO_DECLARADO) {
                continue;
            }
            linhas.add(new String[]{s.getEscopo(), String.valueOf(s.getNivel()), s.getNome(),
                    s.getCategoria().getDescricao(), s.getTipo() == null ? "-" : s.getTipo().getNome(),
                    s.getLinha() == 0 ? "-" : String.valueOf(s.getLinha()), s.descreverParametros()});
        }
        int[] larguras = new int[cabecalho.length];
        for (String[] l : linhas) {
            for (int c = 0; c < l.length; c++) {
                larguras[c] = Math.max(larguras[c], l[c].length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String[] l : linhas) {
            StringBuilder linha = new StringBuilder();
            for (int c = 0; c < l.length; c++) {
                linha.append(l[c]);
                if (c < l.length - 1) {
                    for (int k = l[c].length(); k < larguras[c] + 2; k++) {
                        linha.append(' ');
                    }
                }
            }
            sb.append(semEspacosNoFim(linha.toString())).append('\n');
        }
        return sb.toString();
    }

    private Escopo topo() {
        return pilha.get(pilha.size() - 1);
    }

    private static String chave(String nome) {
        return nome.toLowerCase(Locale.ROOT);
    }

    private static String semEspacosNoFim(String texto) {
        int fim = texto.length();
        while (fim > 0 && texto.charAt(fim - 1) == ' ') {
            fim--;
        }
        return texto.substring(0, fim);
    }
}
