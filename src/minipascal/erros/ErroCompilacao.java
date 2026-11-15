package minipascal.erros;

/**
 * Um erro encontrado durante a análise: a fase que o detectou, a linha do
 * código-fonte e a mensagem. O enunciado da Unidade II pede que cada erro
 * indique o tipo e o número da linha de ocorrência.
 */
public final class ErroCompilacao {

    private final CategoriaErro categoria;
    private final int linha;
    private final String mensagem;

    public ErroCompilacao(CategoriaErro categoria, int linha, String mensagem) {
        this.categoria = categoria;
        this.linha = linha;
        this.mensagem = mensagem;
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

    // Formato usado na saída: "Erro léxico na linha 12: Caractere inválido: @"
    @Override
    public String toString() {
        return categoria.getDescricao() + " na linha " + linha + ": " + mensagem;
    }
}
