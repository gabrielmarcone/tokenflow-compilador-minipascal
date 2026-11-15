package minipascal.lexico.core;

import minipascal.lexico.model.Token;

/**
 * Descreve um token de erro léxico em linguagem de usuário. O lexema do erro
 * já indica o que deu errado: o analisador devolve a string ou o char
 * parcial, o "/*" do comentário aberto, ou o caractere isolado.
 *
 * Usada pela interface (tabela de erros) e pelo analisador sintático
 * (mensagens de erro léxico), para que as duas mostrem o mesmo texto.
 */
public final class DescricaoErroLexico {

    private DescricaoErroLexico() {
    }

    public static String descrever(Token erro) {
        String lexema = erro.getLexema();
        if (lexema.equals("/*")) {
            return "Comentário não fechado (falta */)";
        }
        if (lexema.startsWith("\"")) {
            return "String não fechada (falta \")";
        }
        if (lexema.startsWith("'")) {
            return "Char deve ter 1 caractere";
        }
        return "Caractere inválido";
    }
}
