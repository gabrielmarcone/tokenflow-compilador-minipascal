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
 * Colecao canonica de conjuntos de itens LR(0) e as transicoes entre eles.
 *
 * O estado 0 e closure({S' -> . S}). Os estados sao processados na ordem de
 * criacao; em cada um, os simbolos apos o ponto sao visitados na ordem dos
 * itens, e cada goto ainda nao visto vira o proximo estado. Com isso a
 * numeracao e deterministica e, para a gramatica de expressoes do livro do
 * Dragao (Aho et al.), coincide com a do livro (I0 a I11).
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
        // Cada conjunto de itens ja criado e o numero do estado dele: um goto que
        // da num conjunto conhecido reaproveita o estado em vez de criar outro.
        Map<SortedSet<ItemLR0>, Integer> numeroPorConjunto = new HashMap<>();
        SortedSet<ItemLR0> inicial = new TreeSet<>();
        // Estado 0: closure({S' -> . S}), a producao 0 com o ponto no inicio.
        inicial.add(new ItemLR0(0, 0));
        adicionarEstado(fechamento(inicial), numeroPorConjunto);

        // A lista cresce enquanto e percorrida: cada estado novo tambem sera
        // visitado, ate nao surgir nenhum (e o ponto fixo da construcao).
        for (int i = 0; i < estados.size(); i++) {
            EstadoLR0 estado = estados.get(i);
            for (String simbolo : simbolosAposPonto(estado)) {
                // goto(I, X): para onde o automato vai ao ler X neste estado.
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

    // closure(I): para cada item A -> alfa . B beta, acrescenta B -> . gama para toda producao de B.
    public SortedSet<ItemLR0> fechamento(Set<ItemLR0> itens) {
        SortedSet<ItemLR0> resultado = new TreeSet<>(itens);
        // Itens ainda nao examinados; cada item novo entra aqui uma vez so.
        List<ItemLR0> pendentes = new ArrayList<>(itens);
        while (!pendentes.isEmpty()) {
            ItemLR0 item = pendentes.remove(pendentes.size() - 1);
            String b = item.simboloAposPonto(gramatica);
            // So um nao terminal depois do ponto traz itens novos.
            if (b == null || gramatica.ehTerminal(b)) {
                continue;
            }
            for (Producao p : gramatica.producoesDe(b)) {
                ItemLR0 novo = new ItemLR0(p.getNumero(), 0);
                // add devolve false se o item ja estava: evita laco em B -> B ...
                if (resultado.add(novo)) {
                    pendentes.add(novo);
                }
            }
        }
        return resultado;
    }

    // goto(I, X) = closure dos itens de I com o ponto antes de X, avancados sobre X.
    public SortedSet<ItemLR0> desvio(Set<ItemLR0> itens, String simbolo) {
        Set<ItemLR0> avancados = new LinkedHashSet<>();
        for (ItemLR0 item : itens) {
            if (simbolo.equals(item.simboloAposPonto(gramatica))) {
                // o ponto passa por cima de X
                avancados.add(item.avancar());
            }
        }
        return fechamento(avancados);
    }

    // Simbolos apos o ponto, sem repeticao, na ordem dos itens do estado.
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

    // Estado de destino da transicao, ou -1 se nao houver.
    public int transicao(int estado, String simbolo) {
        Integer destino = transicoes.get(estado).get(simbolo);
        return destino == null ? -1 : destino;
    }

    public Map<String, Integer> transicoesDe(int estado) {
        return Collections.unmodifiableMap(transicoes.get(estado));
    }
}
