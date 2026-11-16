package minipascal.semantico;

import java.util.Locale;

/**
 * Tipos da analise semantica.
 *
 * Os quatro primeiros sao os tipos da linguagem (o terminal "tipo" da
 * gramatica). LOGICO e o tipo do resultado de uma comparacao: a linguagem nao
 * tem variaveis logicas, mas a condicao de um if ou de um while tem de ser
 * logica, e uma comparacao entre parenteses pode aparecer dentro de outra
 * expressao. ERRO e o tipo de uma expressao que ja teve um erro relatado:
 * qualquer operacao com ele da ERRO sem nova mensagem, para que um unico erro
 * nao gere uma cascata de erros.
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

    // char e string: comparaveis entre si e concatenaveis com '+'.
    public boolean isTexto() {
        return this == CHAR || this == STRING;
    }

    /*
     * Um valor deste tipo pode ser guardado numa variavel do tipo destino?
     * Iguais; integer em real (conversao sem perda); char em string.
     * O contrario (real em integer, string em char) e erro.
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
