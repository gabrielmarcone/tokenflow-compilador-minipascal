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
 * Construcao SLR(1) completa de uma gramatica, com conflitos ou sem.
 *
 * Para cada estado I da colecao LR(0):
 *   - item A -> alfa . a beta, com a terminal e goto(I, a) = J:  ACTION[I, a] = empilhar J;
 *   - item A -> alfa ., com A != S':  ACTION[I, b] = reduzir A -> alfa  para todo b em FOLLOW(A);
 *   - item S' -> S .:  ACTION[I, $] = aceitar;
 *   - GOTO[I, A] = goto(I, A) para cada nao terminal A.
 *
 * Cada celula guarda o conjunto de acoes; mais de uma acao e um conflito.
 * O relatorio de tabelas usa esta classe mesmo quando ha conflitos, para
 * mostrar onde eles estao; o analisador sintatico so usa a TabelaSLR, que
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

    // Aplica as tres regras do comentario da classe a cada item de cada estado.
    private void preencherAcoes() {
        for (EstadoLR0 estado : colecao.getEstados()) {
            Map<String, Set<Acao>> linha = new LinkedHashMap<>();
            for (ItemLR0 item : estado.getItens()) {
                String simbolo = item.simboloAposPonto(gramatica);
                if (simbolo != null) {
                    if (gramatica.ehTerminal(simbolo)) {
                        // A -> alfa . a beta: empilha a e vai para goto(I, a)
                        int destino = colecao.transicao(estado.getNumero(), simbolo);
                        adicionar(linha, simbolo, Acao.empilhar(destino));
                    }
                } else if (item.getProducao() == 0) {
                    // S' -> S . : a entrada inteira virou S; so aceita no fim ($)
                    adicionar(linha, Gramatica.FIM_DE_ENTRADA, Acao.aceitar());
                } else {
                    Producao p = gramatica.getProducao(item.getProducao());
                    // A -> alfa . : reduz em todo terminal que pode vir depois de A
                    // (a regra que da nome ao SLR)
                    for (String b : conjuntos.follow(p.getCabeca())) {
                        adicionar(linha, b, Acao.reduzir(p.getNumero()));
                    }
                }
            }
            acoes.add(linha);
        }
    }

    // Junta a acao na celula; uma celula com duas acoes vira conflito depois.
    private static void adicionar(Map<String, Set<Acao>> linha, String terminal, Acao acao) {
        Set<Acao> celula = linha.get(terminal);
        if (celula == null) {
            celula = new TreeSet<>();
            linha.put(terminal, celula);
        }
        celula.add(acao);
    }

    // Lista as celulas com mais de uma acao, em ordem de estado e de terminal.
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

    // Acoes da celula ACTION[estado, terminal] (vazio = erro).
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
