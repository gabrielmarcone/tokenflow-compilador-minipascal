package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Coleção canônica de conjuntos de itens LR(0) e as transições entre eles.
 *
 * O estado 0 é closure({S' -> • S}). Os estados são processados na ordem de
 * criação; em cada um, os símbolos após o ponto são visitados na ordem dos
 * itens, e cada goto ainda não visto vira o próximo estado. Com isso a
 * numeração é determinística e, para a gramática de expressões do livro do
 * Dragão (Aho et al.), coincide com a do livro (I0 a I11).
 */
public final class ColecaoLR0 {

    private final Gramatica gramatica;
    private final List<EstadoLR0> estados = new ArrayList<>();
    private final List<Map<String, Integer>> transicoes = new ArrayList<>();

    public ColecaoLR0(Gramatica gramatica) {
        this.gramatica = gramatica;
        construir();
    }

    private void construir() {
        Map<SortedSet<ItemLR0>, Integer> numeroPorConjunto = new HashMap<>();
        SortedSet<ItemLR0> inicial = new TreeSet<>();
        inicial.add(new ItemLR0(0, 0));
        adicionarEstado(fechamento(inicial), numeroPorConjunto);

        for (int i = 0; i < estados.size(); i++) {
            EstadoLR0 estado = estados.get(i);
            for (String simbolo : simbolosAposPonto(estado)) {
                SortedSet<ItemLR0> destino = desvio(estado.getItens(), simbolo);
                Integer numero = numeroPorConjunto.get(destino);
                if (numero == null) {
                    numero = adicionarEstado(destino, numeroPorConjunto);
                }
                transicoes.get(i).put(simbolo, numero);
            }
        }
    }

    private int adicionarEstado(SortedSet<ItemLR0> itens, Map<SortedSet<ItemLR0>, Integer> numeroPorConjunto) {
        int numero = estados.size();
        estados.add(new EstadoLR0(numero, itens));
        transicoes.add(new HashMap<String, Integer>());
        numeroPorConjunto.put(itens, numero);
        return numero;
    }

    // closure(I): para cada item A -> α • B β, acrescenta B -> • γ para toda produção de B.
    public SortedSet<ItemLR0> fechamento(Set<ItemLR0> itens) {
        SortedSet<ItemLR0> resultado = new TreeSet<>(itens);
        List<ItemLR0> pendentes = new ArrayList<>(itens);
        while (!pendentes.isEmpty()) {
            ItemLR0 item = pendentes.remove(pendentes.size() - 1);
            String b = item.simboloAposPonto(gramatica);
            if (b == null || gramatica.ehTerminal(b)) {
                continue;
            }
            for (Producao p : gramatica.producoesDe(b)) {
                ItemLR0 novo = new ItemLR0(p.getNumero(), 0);
                if (resultado.add(novo)) {
                    pendentes.add(novo);
                }
            }
        }
        return resultado;
    }

    // goto(I, X) = closure dos itens de I com o ponto antes de X, avançados sobre X.
    public SortedSet<ItemLR0> desvio(Set<ItemLR0> itens, String simbolo) {
        Set<ItemLR0> avancados = new LinkedHashSet<>();
        for (ItemLR0 item : itens) {
            if (simbolo.equals(item.simboloAposPonto(gramatica))) {
                avancados.add(item.avancar());
            }
        }
        return fechamento(avancados);
    }

    // Símbolos após o ponto, sem repetição, na ordem dos itens do estado.
    private List<String> simbolosAposPonto(EstadoLR0 estado) {
        Set<String> simbolos = new LinkedHashSet<>();
        for (ItemLR0 item : estado.getItens()) {
            String s = item.simboloAposPonto(gramatica);
            if (s != null) {
                simbolos.add(s);
            }
        }
        return new ArrayList<>(simbolos);
    }

    public List<EstadoLR0> getEstados() {
        return Collections.unmodifiableList(estados);
    }

    public int tamanho() {
        return estados.size();
    }

    // Estado de destino da transição, ou -1 se não houver.
    public int transicao(int estado, String simbolo) {
        Integer destino = transicoes.get(estado).get(simbolo);
        return destino == null ? -1 : destino;
    }

    public Map<String, Integer> transicoesDe(int estado) {
        return Collections.unmodifiableMap(transicoes.get(estado));
    }
}
