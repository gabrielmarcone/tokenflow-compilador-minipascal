package minipascal.sintatico.gramatica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uma producao da gramatica: cabeca -> corpo. O corpo vazio representa a
 * producao vazia (eps). O numero e a posicao na gramatica: 0 e a producao
 * inicial aumentada (S' -> S) e as do arquivo comecam em 1, na ordem em que
 * aparecem. E esse numero que aparece nas reducoes da tabela ("r3").
 */
public final class Producao {

    private final int numero;
    private final String cabeca;
    private final List<String> corpo;

    public Producao(int numero, String cabeca, List<String> corpo) {
        this.numero = numero;
        this.cabeca = cabeca;
        this.corpo = Collections.unmodifiableList(new ArrayList<>(corpo));
    }

    public int getNumero() {
        return numero;
    }

    public String getCabeca() {
        return cabeca;
    }

    public List<String> getCorpo() {
        return corpo;
    }

    // Numero de simbolos do corpo: quantos estados a reducao desempilha.
    public int tamanho() {
        return corpo.size();
    }

    public boolean isVazia() {
        return corpo.isEmpty();
    }

    // "TERMO -> TERMO * FATOR"; a producao vazia aparece como "A -> eps".
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(cabeca).append(" ->");
        if (corpo.isEmpty()) {
            sb.append(" ε");
        }
        for (String simbolo : corpo) {
            sb.append(' ').append(simbolo);
        }
        return sb.toString();
    }
}
