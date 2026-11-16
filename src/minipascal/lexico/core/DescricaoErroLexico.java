package minipascal.lexico.core;

import minipascal.lexico.model.Token;

/**
 * Descreve um token de erro lexico em linguagem de usuario. O lexema do erro
 * ja indica o que deu errado: a string ou o char sem a aspa do fim, o char
 * com tamanho errado ('' ou 'ab'), o numero colado em letras (12abc), o "/*"
 * do comentario aberto ou o caractere fora da linguagem.
 *
 * Usada pela interface (tabela de erros) e pelo analisador sintatico
 * (mensagens de erro lexico), para que as duas mostrem o mesmo texto.
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
            // Fechado ('' ou 'ab'): tamanho errado. Sem a aspa do fim: nao fechado.
            boolean fechado = lexema.length() >= 2 && lexema.endsWith("'");
            return fechado ? "Char deve ter 1 caractere" : "Char não fechado (falta ')";
        }
        if (Character.isDigit(lexema.charAt(0))) {
            return "Número mal formado";
        }
        return "Caractere inválido";
    }
}
