package minipascal.semantico;

/**
 * O que um nome representa na tabela de simbolos. Na gramatica, variavel e
 * procedimento sao o mesmo terminal "id"; e a categoria que diz se um nome
 * pode receber valor, aparecer numa expressao ou ser chamado.
 */
public enum CategoriaSimbolo {

    PROGRAMA("programa"),
    VARIAVEL("variável"),
    PARAMETRO("parâmetro"),
    PARAMETRO_VAR("parâmetro var"),
    PROCEDIMENTO("procedimento"),
    PROCEDIMENTO_PREDEFINIDO("procedimento predefinido"),
    // Nome usado sem declaracao: registrado depois do primeiro erro para que os
    // usos seguintes nao repitam a mesma mensagem.
    NAO_DECLARADO("não declarado");

    private final String descricao;

    CategoriaSimbolo(String descricao) {
        this.descricao = descricao;
    }

    // Variaveis e parametros: tem valor, recebem atribuicao, entram em expressoes.
    public boolean isVariavel() {
        return this == VARIAVEL || this == PARAMETRO || this == PARAMETRO_VAR;
    }

    public boolean isProcedimento() {
        return this == PROCEDIMENTO || this == PROCEDIMENTO_PREDEFINIDO;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
