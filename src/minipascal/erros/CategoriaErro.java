package minipascal.erros;

/**
 * Fase do compilador que detectou o erro. O texto de cada valor é o que
 * aparece nas mensagens mostradas ao usuário.
 */
public enum CategoriaErro {

    LEXICO("Erro léxico"),
    SINTATICO("Erro sintático"),
    SEMANTICO("Erro semântico");

    private final String descricao;

    CategoriaErro(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
