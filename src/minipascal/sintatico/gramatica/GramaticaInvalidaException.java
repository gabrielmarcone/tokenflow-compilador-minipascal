package minipascal.sintatico.gramatica;

/**
 * Erro no arquivo da gramática (sintaxe do arquivo ou gramática mal formada).
 * Quando o problema está numa linha específica, a mensagem traz o número dela.
 */
public class GramaticaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GramaticaInvalidaException(String mensagem) {
        super(mensagem);
    }

    public GramaticaInvalidaException(int linha, String mensagem) {
        super("Gramática, linha " + linha + ": " + mensagem);
    }
}
