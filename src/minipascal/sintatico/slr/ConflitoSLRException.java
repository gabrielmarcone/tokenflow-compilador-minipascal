package minipascal.sintatico.slr;

import java.util.Collections;
import java.util.List;

/**
 * A gramatica nao e SLR(1): a construcao da tabela encontrou conflitos.
 * A mensagem lista todos eles.
 */
public class ConflitoSLRException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ConflitoSLR> conflitos;

    public ConflitoSLRException(List<ConflitoSLR> conflitos) {
        super(montarMensagem(conflitos));
        this.conflitos = Collections.unmodifiableList(conflitos);
    }

    public List<ConflitoSLR> getConflitos() {
        return conflitos;
    }

    private static String montarMensagem(List<ConflitoSLR> conflitos) {
        StringBuilder sb = new StringBuilder("A gramática não é SLR(1): ")
                .append(conflitos.size()).append(" conflito(s).");
        for (ConflitoSLR c : conflitos) {
            sb.append(System.lineSeparator()).append("  ").append(c);
        }
        return sb.toString();
    }
}
