package minipascal.erros;

/**
 * Um erro encontrado durante a analise: a fase que o detectou, a linha do
 * codigo-fonte e a mensagem. O enunciado da Unidade II pede que cada erro
 * indique o tipo e o numero da linha de ocorrencia.
 *
 * A posicao (indice do token na sequencia de tokens lidos pelo lexico) nao
 * aparece na mensagem: serve so para ordenar, na mesma linha, erros de fases
 * diferentes na ordem do texto. Vale -1 quando nao se aplica.
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

    // Indice do token do erro na sequencia de tokens lidos (-1 se nao se aplica).
    public int getPosicao() {
        return posicao;
    }

    // Formato usado na saida: "Erro lexico na linha 12: Caractere invalido: @"
    @Override
    public String toString() {
        return categoria.getDescricao() + " na linha " + linha + ": " + mensagem;
    }
}
