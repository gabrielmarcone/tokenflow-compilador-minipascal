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
    private final int posicao;

    public Terminal(String nome, Token token) {
        this(nome, token, -1);
    }

    /**
     * @param posicao índice do token na sequência de todos os tokens lidos pelo
     *                léxico (inclusive os de erro léxico); ordena erros léxicos e
     *                sintáticos que caem na mesma linha
     */
    public Terminal(String nome, Token token, int posicao) {
        this.nome = nome;
        this.token = token;
        this.linha = token.getLinha();
        this.posicao = posicao;
    }

    // Construtor do fim de arquivo, que não tem token de origem.
    private Terminal(int linha, int posicao) {
        this.nome = FIM_DE_ARQUIVO;
        this.token = null;
        this.linha = linha;
        this.posicao = posicao;
    }

    public static Terminal fimDeArquivo(int linha) {
        return new Terminal(linha, -1);
    }

    public static Terminal fimDeArquivo(int linha, int posicao) {
        return new Terminal(linha, posicao);
    }

    /**
     * Terminal que não está no texto: inserido pela recuperação de erros para
     * que a análise possa continuar (por exemplo, um 'begin' que faltava).
     */
    public static Terminal inserido(String nome, int linha) {
        if (FIM_DE_ARQUIVO.equals(nome)) {
            throw new IllegalArgumentException("o fim de arquivo não é inserido");
        }
        return new Terminal(nome, linha);
    }

    private Terminal(String nome, int linha) {
        this.nome = nome;
        this.token = null;
        this.linha = linha;
        this.posicao = -1;
    }

    public String getNome() {
        return nome;
    }

    // null no fim de arquivo e nos terminais inseridos pela recuperação de erros
    public Token getToken() {
        return token;
    }

    public String getLexema() {
        if (token != null) {
            return token.getLexema();
        }
        return isFimDeArquivo() ? "fim do arquivo" : nome;
    }

    // Verdadeiro para um terminal inserido pela recuperação de erros (não está no texto).
    public boolean isInserido() {
        return token == null && !FIM_DE_ARQUIVO.equals(nome);
    }

    public int getLinha() {
        return linha;
    }

    // Posição na sequência de tokens lidos (-1 se desconhecida).
    public int getPosicao() {
        return posicao;
    }

    public boolean isFimDeArquivo() {
        return token == null && FIM_DE_ARQUIVO.equals(nome);
    }

    @Override
    public String toString() {
        return nome + " (" + getLexema() + ", linha " + linha + ")";
    }
}
