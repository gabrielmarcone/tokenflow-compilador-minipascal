package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calcula, por ponto fixo, os nao terminais anulaveis (que derivam eps) e os
 * conjuntos FIRST e FOLLOW de cada nao terminal.
 *
 *   FIRST(X):  terminais que podem comecar uma cadeia derivada de X
 *              (eps nao entra no conjunto; quem deriva eps esta em "anulaveis").
 *   FOLLOW(A): terminais que podem aparecer logo depois de A em alguma forma
 *              sentencial; FOLLOW(S') contem "$".
 *
 * O SLR(1) usa FOLLOW(A) para decidir em quais terminais reduzir por A -> alfa.
 */
public final class ConjuntosFirstFollow {

    private final Gramatica gramatica;
    private final Set<String> anulaveis = new HashSet<>();
    private final Map<String, Set<String>> first = new HashMap<>();
    private final Map<String, Set<String>> follow = new HashMap<>();

    public ConjuntosFirstFollow(Gramatica gramatica) {
        this.gramatica = gramatica;
        for (Producao p : gramatica.getProducoes()) {
            first.put(p.getCabeca(), new HashSet<String>());
            follow.put(p.getCabeca(), new HashSet<String>());
        }
        calcularAnulaveisEFirst();
        calcularFollow();
    }

    // Ponto fixo: repete a passada por todas as producoes ate nenhum conjunto
    // mudar. Numa producao A -> X1 X2 ... Xn, FIRST(A) recebe FIRST(X1); se X1
    // for anulavel, tambem FIRST(X2), e assim por diante. Se todos os Xi forem
    // anulaveis (ou o corpo for vazio), A e anulavel.
    private void calcularAnulaveisEFirst() {
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (Producao p : gramatica.getProducoes()) {
                Set<String> destino = first.get(p.getCabeca());
                boolean corpoAnulavel = true;
                for (String s : p.getCorpo()) {
                    if (gramatica.ehTerminal(s)) {
                        // um terminal comeca o corpo: entra no FIRST e a leitura do corpo para
                        mudou |= destino.add(s);
                        corpoAnulavel = false;
                        break;
                    }
                    // nao terminal: o que ele pode comecar tambem comeca A; se ele nao
                    // for anulavel, os simbolos seguintes nao chegam a ser o comeco
                    mudou |= destino.addAll(first.get(s));
                    if (!anulaveis.contains(s)) {
                        corpoAnulavel = false;
                        break;
                    }
                }
                if (corpoAnulavel) {
                    mudou |= anulaveis.add(p.getCabeca());
                }
            }
        }
    }

    // Tambem por ponto fixo. FOLLOW(S') = { $ }; para cada A -> alfa B beta,
    // FOLLOW(B) recebe FIRST(beta) e, se beta puder sumir, FOLLOW(A).
    private void calcularFollow() {
        follow.get(gramatica.getSimboloInicialAumentado()).add(Gramatica.FIM_DE_ENTRADA);
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (Producao p : gramatica.getProducoes()) {
                List<String> corpo = p.getCorpo();
                for (int i = 0; i < corpo.size(); i++) {
                    String b = corpo.get(i);
                    // FOLLOW so existe para nao terminais
                    if (gramatica.ehTerminal(b)) {
                        continue;
                    }
                    // FOLLOW(B) recebe FIRST(beta), onde A -> alfa B beta ...
                    List<String> beta = corpo.subList(i + 1, corpo.size());
                    mudou |= follow.get(b).addAll(firstDaSequencia(beta));
                    // ... e FOLLOW(A) se beta for anulavel (ou vazio).
                    if (sequenciaAnulavel(beta)) {
                        mudou |= follow.get(b).addAll(follow.get(p.getCabeca()));
                    }
                }
            }
        }
    }

    // FIRST de uma sequencia de simbolos (sem eps).
    public Set<String> firstDaSequencia(List<String> simbolos) {
        Set<String> resultado = new HashSet<>();
        for (String s : simbolos) {
            if (gramatica.ehTerminal(s)) {
                resultado.add(s);
                return resultado;
            }
            resultado.addAll(first.get(s));
            if (!anulaveis.contains(s)) {
                return resultado;
            }
        }
        return resultado;
    }

    // A sequencia inteira pode derivar a cadeia vazia? (vazia tambem conta)
    public boolean sequenciaAnulavel(List<String> simbolos) {
        for (String s : simbolos) {
            if (gramatica.ehTerminal(s) || !anulaveis.contains(s)) {
                return false;
            }
        }
        return true;
    }

    public boolean ehAnulavel(String naoTerminal) {
        return anulaveis.contains(naoTerminal);
    }

    public Set<String> getAnulaveis() {
        return Collections.unmodifiableSet(anulaveis);
    }

    // FIRST de um simbolo; para terminal e ele mesmo.
    public Set<String> first(String simbolo) {
        if (gramatica.ehTerminal(simbolo)) {
            return Collections.singleton(simbolo);
        }
        return Collections.unmodifiableSet(first.get(simbolo));
    }

    public Set<String> follow(String naoTerminal) {
        return Collections.unmodifiableSet(follow.get(naoTerminal));
    }
}
