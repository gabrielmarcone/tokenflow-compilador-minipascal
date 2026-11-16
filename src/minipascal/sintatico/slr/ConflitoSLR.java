package minipascal.sintatico.slr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Celula da tabela ACTION com mais de uma acao possivel. Se a gramatica gera
 * qualquer conflito, ela nao e SLR(1) e a tabela nao e construida.
 */
public final class ConflitoSLR {

    private final int estado;
    private final String terminal;
    private final List<Acao> acoes;

    public ConflitoSLR(int estado, String terminal, List<Acao> acoes) {
        this.estado = estado;
        this.terminal = terminal;
        List<Acao> ordenadas = new ArrayList<>(acoes);
        Collections.sort(ordenadas);
        this.acoes = Collections.unmodifiableList(ordenadas);
    }

    public int getEstado() {
        return estado;
    }

    public String getTerminal() {
        return terminal;
    }

    public List<Acao> getAcoes() {
        return acoes;
    }

    // "empilhar/reduzir" quando ha um empilhar entre as acoes; senao "reduzir/reduzir".
    public String getTipo() {
        for (Acao a : acoes) {
            if (a.isEmpilhar()) {
                return "empilhar/reduzir";
            }
        }
        return "reduzir/reduzir";
    }

    // "estado 9, terminal 'else': conflito empilhar/reduzir entre s10 e r7"
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("estado ").append(estado)
                .append(", terminal '").append(terminal).append("': conflito ")
                .append(getTipo()).append(" entre ");
        for (int i = 0; i < acoes.size(); i++) {
            if (i > 0) {
                sb.append(i == acoes.size() - 1 ? " e " : ", ");
            }
            sb.append(acoes.get(i));
        }
        return sb.toString();
    }
}
