package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Construção SLR(1) completa de uma gramática, com conflitos ou sem.
 *
 * Para cada estado I da coleção LR(0):
 *   - item A -> α • a β, com a terminal e goto(I, a) = J:  ACTION[I, a] = empilhar J;
 *   - item A -> α •, com A != S':  ACTION[I, b] = reduzir A -> α  para todo b em FOLLOW(A);
 *   - item S' -> S •:  ACTION[I, $] = aceitar;
 *   - GOTO[I, A] = goto(I, A) para cada não terminal A.
 *
 * Cada célula guarda o conjunto de ações; mais de uma ação é um conflito.
 * O relatório de tabelas usa esta classe mesmo quando há conflitos, para
 * mostrar onde eles estão; o analisador sintático só usa a TabelaSLR, que
 * exige zero conflitos.
 */
public final class AnaliseSLR {

    private final Gramatica gramatica;
    private final ConjuntosFirstFollow conjuntos;
    private final ColecaoLR0 colecao;
    private final List<Map<String, Set<Acao>>> acoes = new ArrayList<>();
    private final List<ConflitoSLR> conflitos = new ArrayList<>();

    public AnaliseSLR(Gramatica gramatica) {
        this.gramatica = gramatica;
        this.conjuntos = new ConjuntosFirstFollow(gramatica);
        this.colecao = new ColecaoLR0(gramatica);
        preencherAcoes();
        procurarConflitos();
    }

    private void preencherAcoes() {
        for (EstadoLR0 estado : colecao.getEstados()) {
            Map<String, Set<Acao>> linha = new LinkedHashMap<>();
            for (ItemLR0 item : estado.getItens()) {
                String simbolo = item.simboloAposPonto(gramatica);
                if (simbolo != null) {
                    if (gramatica.ehTerminal(simbolo)) {
                        int destino = colecao.transicao(estado.getNumero(), simbolo);
                        adicionar(linha, simbolo, Acao.empilhar(destino));
                    }
                } else if (item.getProducao() == 0) {
                    adicionar(linha, Gramatica.FIM_DE_ENTRADA, Acao.aceitar());
                } else {
                    Producao p = gramatica.getProducao(item.getProducao());
                    for (String b : conjuntos.follow(p.getCabeca())) {
                        adicionar(linha, b, Acao.reduzir(p.getNumero()));
                    }
                }
            }
            acoes.add(linha);
        }
    }

    private static void adicionar(Map<String, Set<Acao>> linha, String terminal, Acao acao) {
        Set<Acao> celula = linha.get(terminal);
        if (celula == null) {
            celula = new TreeSet<>();
            linha.put(terminal, celula);
        }
        celula.add(acao);
    }

    private void procurarConflitos() {
        for (int estado = 0; estado < acoes.size(); estado++) {
            Map<String, Set<Acao>> linha = acoes.get(estado);
            for (String terminal : gramatica.ordenar(linha.keySet())) {
                Set<Acao> celula = linha.get(terminal);
                if (celula.size() > 1) {
                    conflitos.add(new ConflitoSLR(estado, terminal, new ArrayList<>(celula)));
                }
            }
        }
    }

    public Gramatica getGramatica() {
        return gramatica;
    }

    public ConjuntosFirstFollow getConjuntos() {
        return conjuntos;
    }

    public ColecaoLR0 getColecao() {
        return colecao;
    }

    // Ações da célula ACTION[estado, terminal] (vazio = erro).
    public Set<Acao> acoes(int estado, String terminal) {
        Set<Acao> celula = acoes.get(estado).get(terminal);
        return celula == null ? Collections.<Acao>emptySet() : Collections.unmodifiableSet(celula);
    }

    public List<ConflitoSLR> getConflitos() {
        return Collections.unmodifiableList(conflitos);
    }

    public boolean temConflitos() {
        return !conflitos.isEmpty();
    }
}
