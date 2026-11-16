package minipascal.semantico;

import java.util.Locale;

/**
 * Tipos da análise semântica.
 *
 * Os quatro primeiros são os tipos da linguagem (o terminal "tipo" da
 * gramática). LOGICO é o tipo do resultado de uma comparação: a linguagem não
 * tem variáveis lógicas, mas a condição de um if ou de um while tem de ser
 * lógica, e uma comparação entre parênteses pode aparecer dentro de outra
 * expressão. ERRO é o tipo de uma expressão que já teve um erro relatado:
 * qualquer operação com ele dá ERRO sem nova mensagem, para que um único erro
 * não gere uma cascata de erros.
 */
public enum Tipo {

    INTEGER("integer"),
    REAL("real"),
    CHAR("char"),
    STRING("string"),
    LOGICO("lógico"),
    ERRO("erro");

    private final String nome;

    Tipo(String nome) {
        this.nome = nome;
    }

    // Tipo declarado (o lexema do terminal "tipo"), em qualquer caixa.
    public static Tipo declarado(String lexema) {
        switch (lexema.toLowerCase(Locale.ROOT)) {
            case "integer":
                return INTEGER;
            case "real":
                return REAL;
            case "char":
                return CHAR;
            case "string":
                return STRING;
            default:
                throw new IllegalArgumentException("tipo desconhecido: " + lexema);
        }
    }

    public boolean isNumerico() {
        return this == INTEGER || this == REAL;
    }

    // char e string: comparáveis entre si e concatenáveis com '+'.
    public boolean isTexto() {
        return this == CHAR || this == STRING;
    }

    /*
     * Um valor deste tipo pode ser guardado numa variável do tipo destino?
     * Iguais; integer em real (conversão sem perda); char em string.
     * O contrário (real em integer, string em char) é erro.
     */
    public boolean podeSerAtribuidoA(Tipo destino) {
        return this == destino
                || (this == INTEGER && destino == REAL)
                || (this == CHAR && destino == STRING);
    }

    // Nome usado nas mensagens.
    public String getNome() {
        return nome;
    }

    @Override
    public String toString() {
        return nome;
    }
}
