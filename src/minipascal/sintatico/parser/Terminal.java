package minipascal.sintatico.parser;

import minipascal.lexico.model.Token;

/**
 * Símbolo terminal entregue ao analisador sintático. O nome é o terminal da
 * gramática ("id", "numero", "begin", ":=", "$"...); o token de origem guarda
 * o lexema e a linha, usados nas mensagens de erro e nas ações semânticas.
 */
public final class Terminal {

    // Terminais que representam classes de lexemas (os demais são o próprio lexema).
    public static final String ID = "id";
    public static final String NUMERO = "numero";
    public static final String TIPO = "tipo";
    public static final String CONSTANTE_STRING = "constante_string";
    public static final String CONSTANTE_CHAR = "constante_char";
    public static final String FIM_DE_ARQUIVO = "$";

    private final String nome;
    private final Token token;
    private final int linha;

    public Terminal(String nome, Token token) {
        this.nome = nome;
        this.token = token;
        this.linha = token.getLinha();
    }

    // Construtor do fim de arquivo, que não tem token de origem.
    private Terminal(int linha) {
        this.nome = FIM_DE_ARQUIVO;
        this.token = null;
        this.linha = linha;
    }

    public static Terminal fimDeArquivo(int linha) {
        return new Terminal(linha);
    }

    public String getNome() {
        return nome;
    }

    // null apenas no fim de arquivo
    public Token getToken() {
        return token;
    }

    public String getLexema() {
        return token == null ? "fim do arquivo" : token.getLexema();
    }

    public int getLinha() {
        return linha;
    }

    public boolean isFimDeArquivo() {
        return token == null;
    }

    @Override
    public String toString() {
        return nome + " (" + getLexema() + ", linha " + linha + ")";
    }
}
