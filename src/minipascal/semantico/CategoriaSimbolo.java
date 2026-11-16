package minipascal.semantico;

/**
 * O que um nome representa na tabela de símbolos. Na gramática, variável e
 * procedimento são o mesmo terminal "id"; é a categoria que diz se um nome
 * pode receber valor, aparecer numa expressão ou ser chamado.
 */
public enum CategoriaSimbolo {

    PROGRAMA("programa"),
    VARIAVEL("variável"),
    PARAMETRO("parâmetro"),
    PARAMETRO_VAR("parâmetro var"),
    PROCEDIMENTO("procedimento"),
    PROCEDIMENTO_PREDEFINIDO("procedimento predefinido"),
    // Nome usado sem declaração: registrado depois do primeiro erro para que os
    // usos seguintes não repitam a mesma mensagem.
    NAO_DECLARADO("não declarado");

    private final String descricao;

    CategoriaSimbolo(String descricao) {
        this.descricao = descricao;
    }

    // Variáveis e parâmetros: têm valor, recebem atribuição, entram em expressões.
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
