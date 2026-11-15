package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tabelas ACTION e GOTO de um analisador SLR(1), sem conflitos.
 *
 * É montada uma única vez, a partir da gramática, antes da análise (a mesma
 * ideia da tabela de palavras reservadas da Unidade I). Se a gramática tiver
 * qualquer conflito, construir() lança ConflitoSLRException listando todos:
 * nenhuma tabela ambígua chega ao analisador sintático.
 */
public final class TabelaSLR {

    private final AnaliseSLR analise;
    private final List<Map<String, Acao>> acao = new ArrayList<>();
    private final List<Map<String, Integer>> desvio = new ArrayList<>();

    private TabelaSLR(AnaliseSLR analise) {
        this.analise = analise;
        Gramatica g = analise.getGramatica();
        ColecaoLR0 colecao = analise.getColecao();
        for (int estado = 0; estado < colecao.tamanho(); estado++) {
            Map<String, Acao> linhaAcao = new HashMap<>();
            for (String terminal : g.getTerminais()) {
                Set<Acao> celula = analise.acoes(estado, terminal);
                if (!celula.isEmpty()) {
                    linhaAcao.put(terminal, celula.iterator().next());
                }
            }
            Map<String, Integer> linhaDesvio = new HashMap<>();
            for (String naoTerminal : g.getNaoTerminais()) {
                int destino = colecao.transicao(estado, naoTerminal);
                if (destino >= 0) {
                    linhaDesvio.put(naoTerminal, destino);
                }
            }
            acao.add(linhaAcao);
            desvio.add(linhaDesvio);
        }
    }

    public static TabelaSLR construir(Gramatica gramatica) {
        AnaliseSLR analise = new AnaliseSLR(gramatica);
        if (analise.temConflitos()) {
            throw new ConflitoSLRException(new ArrayList<>(analise.getConflitos()));
        }
        return new TabelaSLR(analise);
    }

    // ACTION[estado, terminal], ou null se a célula estiver vazia (erro sintático).
    public Acao acao(int estado, String terminal) {
        return acao.get(estado).get(terminal);
    }

    // GOTO[estado, não terminal], ou -1 se não houver transição.
    public int desvio(int estado, String naoTerminal) {
        Integer destino = desvio.get(estado).get(naoTerminal);
        return destino == null ? -1 : destino;
    }

    // Terminais com ação no estado, na ordem da gramática: é o que o
    // analisador esperava ver quando encontra um erro nesse estado.
    public List<String> terminaisEsperados(int estado) {
        return Collections.unmodifiableList(analise.getGramatica().ordenar(acao.get(estado).keySet()));
    }

    public int numeroDeEstados() {
        return acao.size();
    }

    public Gramatica getGramatica() {
        return analise.getGramatica();
    }

    public AnaliseSLR getAnalise() {
        return analise;
    }
}
