package minipascal.sintatico.gramatica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uma produção da gramática: cabeça -> corpo. O corpo vazio representa a
 * produção vazia (ε). O número é a posição na gramática: 0 é a produção
 * inicial aumentada (S' -> S) e as do arquivo começam em 1, na ordem em que
 * aparecem. É esse número que aparece nas reduções da tabela ("r3").
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

    public int tamanho() {
        return corpo.size();
    }

    public boolean isVazia() {
        return corpo.isEmpty();
    }

    // "TERMO -> TERMO * FATOR"; a produção vazia aparece como "A -> ε".
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
