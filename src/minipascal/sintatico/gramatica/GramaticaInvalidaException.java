package minipascal.sintatico.gramatica;

/**
 * Erro no arquivo da gramatica (sintaxe do arquivo ou gramatica mal formada).
 * Quando o problema esta numa linha especifica, a mensagem traz o numero dela.
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
