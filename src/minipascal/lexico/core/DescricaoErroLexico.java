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

    // Marca que aparece no lugar de um caractere de controle: um quadrado
    // vazio, que existe em todas as fontes de largura fixa.
    public static final char MARCA_INVISIVEL = '□';

    /*
     * Caracteres de controle, que nao se veem: os de codigo abaixo de 32 (fora
     * a tabulacao e as quebras de linha) e o DEL (127). A interface grafica
     * nao consegue mostra-los (o JavaFX os apaga do texto).
     */
    public static boolean invisivel(char c) {
        return (c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0x7F;
    }

    /*
     * O texto com cada caractere de controle trocado pela marca. Usado nas
     * mensagens de erro (um "Caractere invalido: " seguido de nada nao diz
     * nada) e pela interface ao carregar um arquivo: assim a linha de comando
     * e a interface mostram a mesma mensagem, na mesma linha.
     */
    public static String visivel(String texto) {
        StringBuilder sb = null;
        for (int i = 0; i < texto.length(); i++) {
            if (invisivel(texto.charAt(i))) {
                if (sb == null) {
                    sb = new StringBuilder(texto);
                }
                sb.setCharAt(i, MARCA_INVISIVEL);
            }
        }
        return sb == null ? texto : sb.toString();
    }
}
