package minipascal.semantico;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tabela de simbolos com escopos aninhados: uma tabela hash por escopo,
 * empilhadas (livro do Dragao, secao 2.7).
 *
 *   - abrirEscopo: empilha uma tabela nova (o bloco de um procedimento);
 *   - declarar: insere no escopo do topo; recusa um nome repetido nele;
 *   - buscar: procura do topo para a base, entao o nome local esconde o de
 *     fora (um parametro x esconde uma variavel global x);
 *   - fecharEscopo: desempilha, e os nomes locais deixam de ser visiveis.
 *
 * Escopos: 0 "externo" (read, write, writeln e o nome do programa, que ficam
 * fora do programa e podem ser redeclarados nele); 1 "global" (variaveis e
 * procedimentos do programa); 2 em diante, dentro dos procedimentos.
 *
 * Os nomes sao comparados sem diferenciar maiusculas de minusculas, como no
 * Pascal e como as palavras reservadas do lexico: Soma e soma sao o mesmo nome.
 *
 * Alem da pilha, a tabela guarda o historico de todos os simbolos declarados,
 * na ordem da declaracao, para mostrar a tabela inteira depois da analise
 * (os escopos ja fechados nao estao mais na pilha).
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

    // Comeca com o escopo externo ja aberto.
    public TabelaSimbolos() {
        pilha.add(new Escopo(ESCOPO_EXTERNO));
    }

    /**
     * Abre um escopo dentro do atual. O nome completo junta os nomes dos
     * procedimentos que o contem ("soma.aux"); o global se chama "global".
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

    // Nivel do escopo atual (0 = externo).
    public int nivelAtual() {
        return pilha.size() - 1;
    }

    public String escopoAtual() {
        return topo().nome;
    }

    /**
     * Declara no escopo atual.
     *
     * @return null se deu certo; se o nome ja existe neste escopo, o simbolo que
     *         ja estava (o novo nao e inserido)
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

    // O simbolo visivel com esse nome (do escopo mais interno para o externo), ou null.
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

    // So no escopo atual, ou null.
    public Simbolo buscarNoEscopoAtual(String nome) {
        return topo().simbolos.get(chave(nome));
    }

    // Todos os simbolos declarados, na ordem da declaracao (inclusive os de escopos ja fechados).
    public List<Simbolo> getHistorico() {
        return Collections.unmodifiableList(historico);
    }

    /**
     * A tabela inteira em texto, uma linha por simbolo, sem os nomes "nao
     * declarados" (que so existem para evitar mensagens repetidas):
     *
     *   Escopo   Nivel  Nome   Categoria  Tipo     Linha  Parametros
     *   global   1      x      variavel   integer  2
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
