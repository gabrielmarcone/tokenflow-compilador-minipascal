package minipascal.lexico.model;

/**
 * Classes lexicas da linguagem. O texto de cada valor e o que aparece na
 * coluna de token do arquivo de saida (sem codigo numerico).
 */
public enum TipoToken {

    PALAVRA_RESERVADA("Palavra reservada"),
    IDENTIFICADOR("Identificador"),
    NUMERO_INTEIRO("Número inteiro"),
    NUMERO_REAL("Número real"),
    OPERADOR_ARITMETICO("Operador aritmético"),
    OPERADOR_RELACIONAL("Operador relacional"),
    OPERADOR_LOGICO("Operador lógico"),
    SIMBOLO_ESPECIAL("Símbolo especial"),
    ATRIBUICAO("Atribuição"),
    FIM("Fim"),
    CONSTANTE_STRING("Constante string"),
    CONSTANTE_CHAR("Constante char"),
    ERRO_LEXICO("Erro léxico");

    private final String descricao;

    TipoToken(String descricao) {
        this.descricao = descricao;
    }

    // Texto da classe, com acentos (vai para o arquivo de saida em UTF-8).
    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
