package minipascal.erros;

/**
 * Um erro encontrado durante a análise: a fase que o detectou, a linha do
 * código-fonte e a mensagem. O enunciado da Unidade II pede que cada erro
 * indique o tipo e o número da linha de ocorrência.
 *
 * A posição (índice do token na sequência de tokens lidos pelo léxico) não
 * aparece na mensagem: serve só para ordenar, na mesma linha, erros de fases
 * diferentes na ordem do texto. Vale -1 quando não se aplica.
 */
public final class ErroCompilacao {

    private final CategoriaErro categoria;
    private final int linha;
    private final String mensagem;
    private final int posicao;

    public ErroCompilacao(CategoriaErro categoria, int linha, String mensagem) {
        this(categoria, linha, mensagem, -1);
    }

    public ErroCompilacao(CategoriaErro categoria, int linha, String mensagem, int posicao) {
        this.categoria = categoria;
        this.linha = linha;
        this.mensagem = mensagem;
        this.posicao = posicao;
    }

    public CategoriaErro getCategoria() {
        return categoria;
    }

    public int getLinha() {
        return linha;
    }

    public String getMensagem() {
        return mensagem;
    }

    // Índice do token do erro na sequência de tokens lidos (-1 se não se aplica).
    public int getPosicao() {
        return posicao;
    }

    // Formato usado na saída: "Erro léxico na linha 12: Caractere inválido: @"
    @Override
    public String toString() {
        return categoria.getDescricao() + " na linha " + linha + ": " + mensagem;
    }
}
