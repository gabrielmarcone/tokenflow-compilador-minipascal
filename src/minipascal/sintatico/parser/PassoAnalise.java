package minipascal.sintatico.parser;

import java.util.Arrays;

/**
 * Um passo da análise sintática, como nas tabelas de movimentos do livro do
 * Dragão (figura 4.38): a pilha de estados, os símbolos correspondentes, o
 * terminal de entrada e a ação feita.
 */
public final class PassoAnalise {

    public enum Tipo { EMPILHAR, REDUZIR, ACEITAR, ERRO, RECUPERACAO }

    private final int numero;
    private final Tipo tipo;
    private final int[] estados;
    private final String[] simbolos;
    private final String terminal;
    private final String lexema;
    private final boolean inserido;
    private final int posicaoEntrada;
    private final int linha;
    private final String codigo;
    private final String descricao;

    /**
     * @param numero         1, 2, 3...
     * @param tipo           o que aconteceu
     * @param estados        pilha de estados (base no índice 0)
     * @param simbolos       o símbolo de cada estado da pilha ("" para o estado 0)
     * @param terminal       nome do terminal de entrada ("id", ";", "$"...)
     * @param lexema         o texto dele ("x", ";", "fim do arquivo"...)
     * @param inserido       se o terminal foi inserido pela recuperação de erros
     * @param posicaoEntrada posição do terminal de entrada (ou, se inserido, do
     *                       primeiro token do texto que vem depois dele) na
     *                       sequência de tokens lidos
     * @param linha          linha do terminal de entrada
     * @param codigo         ação como na tabela ("s7", "r23", "acc"), ou "" para erro e recuperação
     * @param descricao      a ação por extenso
     */
    PassoAnalise(int numero, Tipo tipo, int[] estados, String[] simbolos, String terminal, String lexema,
                 boolean inserido, int posicaoEntrada, int linha, String codigo, String descricao) {
        this.numero = numero;
        this.tipo = tipo;
        this.estados = estados;
        this.simbolos = simbolos;
        this.terminal = terminal;
        this.lexema = lexema;
        this.inserido = inserido;
        this.posicaoEntrada = posicaoEntrada;
        this.linha = linha;
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public int getNumero() {
        return numero;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public int[] getEstados() {
        return Arrays.copyOf(estados, estados.length);
    }

    public int getTopo() {
        return estados[estados.length - 1];
    }

    public String[] getSimbolos() {
        return Arrays.copyOf(simbolos, simbolos.length);
    }

    public String getTerminal() {
        return terminal;
    }

    public String getLexema() {
        return lexema;
    }

    public boolean isInserido() {
        return inserido;
    }

    public int getPosicaoEntrada() {
        return posicaoEntrada;
    }

    public int getLinha() {
        return linha;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    // "0 2 5 7"
    public String pilhaComoTexto() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < estados.length; i++) {
            sb.append(i == 0 ? "" : " ").append(estados[i]);
        }
        return sb.toString();
    }

    // "program id ; BLOCO_VAR" (os símbolos dos estados, sem o do estado 0)
    public String simbolosComoTexto() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < simbolos.length; i++) {
            sb.append(i == 1 ? "" : " ").append(simbolos[i]);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return numero + " | " + pilhaComoTexto() + " | " + simbolosComoTexto() + " | " + lexema + " | " + descricao;
    }
}
