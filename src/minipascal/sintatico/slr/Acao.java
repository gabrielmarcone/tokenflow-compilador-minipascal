package minipascal.sintatico.slr;

/**
 * Uma entrada da tabela ACTION: empilhar e ir para um estado ("s5"),
 * reduzir por uma producao ("r3") ou aceitar ("acc").
 */
public final class Acao implements Comparable<Acao> {

    public enum Tipo { EMPILHAR, REDUZIR, ACEITAR }

    // Aceitar nao tem alvo; um objeto so serve para todas as celulas.
    private static final Acao ACEITAR = new Acao(Tipo.ACEITAR, 0);

    private final Tipo tipo;
    private final int alvo;

    private Acao(Tipo tipo, int alvo) {
        this.tipo = tipo;
        this.alvo = alvo;
    }

    public static Acao empilhar(int estado) {
        return new Acao(Tipo.EMPILHAR, estado);
    }

    public static Acao reduzir(int producao) {
        return new Acao(Tipo.REDUZIR, producao);
    }

    public static Acao aceitar() {
        return ACEITAR;
    }

    public Tipo getTipo() {
        return tipo;
    }

    // Estado de destino (empilhar) ou numero da producao (reduzir).
    public int getAlvo() {
        return alvo;
    }

    public boolean isEmpilhar() {
        return tipo == Tipo.EMPILHAR;
    }

    public boolean isReduzir() {
        return tipo == Tipo.REDUZIR;
    }

    public boolean isAceitar() {
        return tipo == Tipo.ACEITAR;
    }

    @Override
    // Texto usado nas tabelas: s5, r3 ou acc.
    public String toString() {
        switch (tipo) {
            case EMPILHAR:
                return "s" + alvo;
            case REDUZIR:
                return "r" + alvo;
            default:
                return "acc";
        }
    }

    // Ordem usada so para listar conflitos sempre do mesmo jeito.
    @Override
    public int compareTo(Acao outra) {
        if (tipo != outra.tipo) {
            return tipo.compareTo(outra.tipo);
        }
        return Integer.compare(alvo, outra.alvo);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Acao)) {
            return false;
        }
        Acao outra = (Acao) o;
        return tipo == outra.tipo && alvo == outra.alvo;
    }

    @Override
    public int hashCode() {
        return 31 * tipo.hashCode() + alvo;
    }
}
