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
 * Calcula, por ponto fixo, os não terminais anuláveis (que derivam ε) e os
 * conjuntos FIRST e FOLLOW de cada não terminal.
 *
 *   FIRST(X):  terminais que podem começar uma cadeia derivada de X
 *              (ε não entra no conjunto; quem deriva ε está em "anuláveis").
 *   FOLLOW(A): terminais que podem aparecer logo depois de A em alguma forma
 *              sentencial; FOLLOW(S') contém "$".
 *
 * O SLR(1) usa FOLLOW(A) para decidir em quais terminais reduzir por A -> α.
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

    private void calcularAnulaveisEFirst() {
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (Producao p : gramatica.getProducoes()) {
                Set<String> destino = first.get(p.getCabeca());
                boolean corpoAnulavel = true;
                for (String s : p.getCorpo()) {
                    if (gramatica.ehTerminal(s)) {
                        mudou |= destino.add(s);
                        corpoAnulavel = false;
                        break;
                    }
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

    private void calcularFollow() {
        follow.get(gramatica.getSimboloInicialAumentado()).add(Gramatica.FIM_DE_ENTRADA);
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (Producao p : gramatica.getProducoes()) {
                List<String> corpo = p.getCorpo();
                for (int i = 0; i < corpo.size(); i++) {
                    String b = corpo.get(i);
                    if (gramatica.ehTerminal(b)) {
                        continue;
                    }
                    // FOLLOW(B) recebe FIRST(β), onde A -> α B β ...
                    List<String> beta = corpo.subList(i + 1, corpo.size());
                    mudou |= follow.get(b).addAll(firstDaSequencia(beta));
                    // ... e FOLLOW(A) se β for anulável (ou vazio).
                    if (sequenciaAnulavel(beta)) {
                        mudou |= follow.get(b).addAll(follow.get(p.getCabeca()));
                    }
                }
            }
        }
    }

    // FIRST de uma sequência de símbolos (sem ε).
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

    // FIRST de um símbolo; para terminal é ele mesmo.
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
