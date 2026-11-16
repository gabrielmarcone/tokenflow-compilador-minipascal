package minipascal.sintatico.gramatica;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gramatica livre de contexto usada pelo gerador SLR(1).
 *
 * Convencoes:
 *   - nao terminais sao escritos em MAIUSCULAS (letras, digitos e _);
 *   - qualquer outro simbolo e terminal ("id", "numero", "begin", ":=", ";"...);
 *   - o simbolo inicial e a cabeca da primeira producao;
 *   - "$" e o fim de entrada e nao pode aparecer nas producoes.
 *
 * A gramatica e aumentada com a producao 0, S' -> S, onde S e o simbolo inicial.
 * Na construcao sao recusadas gramaticas com producao repetida, nao terminal
 * sem producao, nao terminal inalcancavel a partir do simbolo inicial e nao
 * terminal que nao deriva nenhuma cadeia de terminais: todos sao, em geral,
 * erro de digitacao.
 */
public final class Gramatica {

    public static final String FIM_DE_ENTRADA = "$";

    private final List<Producao> producoes;
    private final String simboloInicial;
    private final String simboloInicialAumentado;
    private final Set<String> naoTerminais;
    private final Set<String> terminais;
    private final Map<String, List<Producao>> producoesPorCabeca;
    private final Map<String, Integer> ordemDosSimbolos;

    /**
     * Recebe as producoes na ordem do arquivo (numeradas a partir de 1) e
     * acrescenta a producao 0, S' -> S.
     */
    public Gramatica(List<Producao> producoesDoArquivo) {
        if (producoesDoArquivo.isEmpty()) {
            throw new GramaticaInvalidaException("a gramática não tem nenhuma produção.");
        }
        // O simbolo inicial e a cabeca da primeira regra do arquivo.
        this.simboloInicial = producoesDoArquivo.get(0).getCabeca();
        this.simboloInicialAumentado = simboloInicial + "'";

        List<Producao> todas = new ArrayList<>();
        // Producao 0, a aumentada S' -> S: reduzir por ela e aceitar o programa.
        todas.add(new Producao(0, simboloInicialAumentado, Collections.singletonList(simboloInicial)));
        for (int i = 0; i < producoesDoArquivo.size(); i++) {
            Producao p = producoesDoArquivo.get(i);
            if (p.getNumero() != i + 1) {
                throw new GramaticaInvalidaException("produções devem ser numeradas a partir de 1, em ordem.");
            }
            todas.add(p);
        }
        this.producoes = Collections.unmodifiableList(todas);

        // Nao terminais: as cabecas, na ordem do arquivo; e as producoes de cada um.
        Set<String> nts = new LinkedHashSet<>();
        Map<String, List<Producao>> porCabeca = new HashMap<>();
        Map<String, Integer> jaVista = new HashMap<>();
        for (Producao p : producoesDoArquivo) {
            // Producao repetida geraria um conflito reduzir/reduzir entre as duas
            // copias; quase sempre e erro de digitacao, entao e recusada.
            Integer anterior = jaVista.put(p.toString(), p.getNumero());
            if (anterior != null) {
                throw new GramaticaInvalidaException("a produção " + p.getNumero() + " (" + p
                        + ") repete a produção " + anterior + ".");
            }
            if (!ehNomeDeNaoTerminal(p.getCabeca())) {
                throw new GramaticaInvalidaException("a cabeça '" + p.getCabeca()
                        + "' não é um nome de não terminal (use MAIÚSCULAS, dígitos e _).");
            }
            nts.add(p.getCabeca());
            porCabeca.computeIfAbsent(p.getCabeca(), k -> new ArrayList<>()).add(p);
        }
        porCabeca.put(simboloInicialAumentado, Collections.singletonList(todas.get(0)));

        // Terminais: todo simbolo do corpo que nao e nome de nao terminal. Um nome
        // em MAIUSCULAS sem producao e erro (provavelmente digitado errado).
        Set<String> ts = new LinkedHashSet<>();
        for (Producao p : producoesDoArquivo) {
            for (String s : p.getCorpo()) {
                if (s.equals(FIM_DE_ENTRADA)) {
                    throw new GramaticaInvalidaException("o símbolo '$' é reservado para o fim de entrada"
                            + " (produção " + p.getNumero() + ").");
                }
                if (ehNomeDeNaoTerminal(s)) {
                    if (!nts.contains(s)) {
                        throw new GramaticaInvalidaException("o não terminal '" + s
                                + "' é usado na produção " + p.getNumero() + " mas não tem produção.");
                    }
                } else {
                    ts.add(s);
                }
            }
        }
        // o $ (fim de entrada) e sempre o ultimo terminal
        ts.add(FIM_DE_ENTRADA);

        this.naoTerminais = Collections.unmodifiableSet(nts);
        this.terminais = Collections.unmodifiableSet(ts);
        Map<String, List<Producao>> porCabecaImutavel = new HashMap<>();
        for (Map.Entry<String, List<Producao>> e : porCabeca.entrySet()) {
            porCabecaImutavel.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        this.producoesPorCabeca = Collections.unmodifiableMap(porCabecaImutavel);

        // Ordem fixa dos simbolos: terminais na ordem em que aparecem, "$" por
        // ultimo, depois os nao terminais. Usada para listar conjuntos e
        // colunas sempre na mesma ordem.
        Map<String, Integer> ordem = new HashMap<>();
        for (String t : ts) {
            ordem.put(t, ordem.size());
        }
        for (String n : nts) {
            ordem.put(n, ordem.size());
        }
        this.ordemDosSimbolos = Collections.unmodifiableMap(ordem);

        verificarAlcancaveis();
        verificarProdutivos();
    }

    // MAIUSCULAS, digitos e _, comecando por letra (EXPR, BLOCO_VAR, CAB_PROC).
    public static boolean ehNomeDeNaoTerminal(String simbolo) {
        if (simbolo.isEmpty() || simbolo.charAt(0) < 'A' || simbolo.charAt(0) > 'Z') {
            return false;
        }
        for (int i = 1; i < simbolo.length(); i++) {
            char c = simbolo.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // Busca a partir do simbolo inicial: um nao terminal que nunca aparece em
    // nenhuma derivacao e sinal de erro na gramatica.
    private void verificarAlcancaveis() {
        Set<String> alcancados = new HashSet<>();
        List<String> pendentes = new ArrayList<>();
        alcancados.add(simboloInicial);
        pendentes.add(simboloInicial);
        while (!pendentes.isEmpty()) {
            String a = pendentes.remove(pendentes.size() - 1);
            for (Producao p : producoesPorCabeca.get(a)) {
                for (String s : p.getCorpo()) {
                    if (naoTerminais.contains(s) && alcancados.add(s)) {
                        pendentes.add(s);
                    }
                }
            }
        }
        for (String n : naoTerminais) {
            if (!alcancados.contains(n)) {
                throw new GramaticaInvalidaException("o não terminal '" + n
                        + "' não é alcançável a partir de '" + simboloInicial + "'.");
            }
        }
    }

    // Ponto fixo: um nao terminal e produtivo se tem uma producao cujo corpo so
    // tem terminais e nao terminais ja produtivos. Um que nunca fica produtivo
    // so tem recursao sem saida (A -> a A) e nao gera nenhuma cadeia.
    private void verificarProdutivos() {
        Set<String> produtivos = new HashSet<>();
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (Producao p : producoes) {
                if (produtivos.contains(p.getCabeca())) {
                    continue;
                }
                boolean todos = true;
                for (String s : p.getCorpo()) {
                    if (naoTerminais.contains(s) && !produtivos.contains(s)) {
                        todos = false;
                        break;
                    }
                }
                if (todos) {
                    produtivos.add(p.getCabeca());
                    mudou = true;
                }
            }
        }
        for (String n : naoTerminais) {
            if (!produtivos.contains(n)) {
                throw new GramaticaInvalidaException("o não terminal '" + n
                        + "' não deriva nenhuma cadeia de terminais (recursão sem saída).");
            }
        }
    }

    // Todas as producoes; a de indice 0 e a aumentada S' -> S.
    public List<Producao> getProducoes() {
        return producoes;
    }

    public Producao getProducao(int numero) {
        return producoes.get(numero);
    }

    public List<Producao> producoesDe(String naoTerminal) {
        List<Producao> lista = producoesPorCabeca.get(naoTerminal);
        return lista == null ? Collections.<Producao>emptyList() : lista;
    }

    public String getSimboloInicial() {
        return simboloInicial;
    }

    public String getSimboloInicialAumentado() {
        return simboloInicialAumentado;
    }

    // Nao terminais na ordem das cabecas (sem o S' aumentado).
    public Set<String> getNaoTerminais() {
        return naoTerminais;
    }

    // Terminais na ordem em que aparecem nas producoes, com "$" no fim.
    public Set<String> getTerminais() {
        return terminais;
    }

    public boolean ehTerminal(String simbolo) {
        return terminais.contains(simbolo);
    }

    public boolean ehNaoTerminal(String simbolo) {
        return naoTerminais.contains(simbolo) || simbolo.equals(simboloInicialAumentado);
    }

    // Devolve os simbolos na ordem fixa da gramatica (para relatorios e testes).
    // Devolve os simbolos na ordem fixa da gramatica (para relatorios, mensagens e testes).
    public List<String> ordenar(Collection<String> simbolos) {
        List<String> lista = new ArrayList<>(simbolos);
        lista.sort((a, b) -> Integer.compare(posicao(a), posicao(b)));
        return lista;
    }

    private int posicao(String simbolo) {
        Integer p = ordemDosSimbolos.get(simbolo);
        return p == null ? Integer.MAX_VALUE : p;
    }
}
