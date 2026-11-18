package minipascal.sintatico.parser;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.core.AnalisadorLexico;
import minipascal.lexico.core.DescricaoErroLexico;
import minipascal.lexico.model.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Liga o analisador lexico da Unidade I ao analisador sintatico. O lexico
 * continua devolvendo pares lexema/classe (Token); o adaptador traduz cada
 * Token para o terminal que a gramatica espera:
 *
 *   - integer, real, char, string      -> "tipo"
 *   - demais palavras reservadas       -> a propria palavra, em minusculas
 *   - identificador                    -> "id"
 *   - numero inteiro ou real           -> "numero"
 *   - operadores logicos e aritmeticos -> o proprio lexema, em minusculas
 *     (mod, and, or, not)
 *   - operadores relacionais, simbolos especiais, ":=" e "." -> o lexema
 *   - constante string / char          -> "constante_string" / "constante_char"
 *   - erro lexico                      -> nao vira terminal: e registrado como
 *     erro e o adaptador segue para o proximo token
 *   - fim do texto (null)              -> "$"
 *
 * Assim o analisador lexico nao muda e a saida da Unidade I continua igual.
 */
public class AdaptadorLexico {

    private final AnalisadorLexico lexico;
    private final List<Token> tokensLidos = new ArrayList<>();
    private final List<ErroCompilacao> errosLexicos = new ArrayList<>();
    // Posicao (em tokensLidos) do token de cada erro lexico, na mesma ordem de errosLexicos.
    private final List<Integer> posicoesDosErrosLexicos = new ArrayList<>();
    private int ultimaLinha = 1;
    private boolean terminou = false;

    public AdaptadorLexico(AnalisadorLexico lexico) {
        this.lexico = lexico;
    }

    // Devolve o proximo terminal. No fim do texto devolve "$" e continua
    // devolvendo "$" em chamadas seguintes (nunca devolve null).
    public Terminal proximo() {
        while (!terminou) {
            Token token = lexico.proximoToken();
            if (token == null) {
                terminou = true;
                break;
            }
            // Todo token fica guardado, inclusive os erros (aba Tokens, posicao dos erros).
            tokensLidos.add(token);
            ultimaLinha = token.getLinha();

            int posicao = tokensLidos.size() - 1;
            String nome = nomeDoTerminal(token);
            // Token valido: vira terminal da gramatica.
            if (nome != null) {
                return new Terminal(nome, token, posicao);
            }
            // Erro lexico: registra e pede o proximo token (modo panico do lexico).
            errosLexicos.add(new ErroCompilacao(CategoriaErro.LEXICO, token.getLinha(),
                    DescricaoErroLexico.descrever(token) + ": " + DescricaoErroLexico.visivel(token.getLexema()),
                    posicao));
            posicoesDosErrosLexicos.add(posicao);
        }
        return Terminal.fimDeArquivo(ultimaLinha, tokensLidos.size());
    }

    // Posicao (na sequencia de tokens lidos) do token do i-esimo erro lexico.
    public int posicaoDoErroLexico(int i) {
        return posicoesDosErrosLexicos.get(i);
    }

    // Nome do terminal da gramatica para um token, ou null se for erro lexico.
    public static String nomeDoTerminal(Token token) {
        String lexema = token.getLexema();
        switch (token.getTipo()) {
            case PALAVRA_RESERVADA:
                String palavra = lexema.toLowerCase(Locale.ROOT);
                if (palavra.equals("integer") || palavra.equals("real")
                        || palavra.equals("char") || palavra.equals("string")) {
                    return Terminal.TIPO;
                }
                return palavra;
            case IDENTIFICADOR:
                return Terminal.ID;
            case NUMERO_INTEIRO:
            case NUMERO_REAL:
                return Terminal.NUMERO;
            case OPERADOR_ARITMETICO:
            case OPERADOR_LOGICO:
                // "mod", "and", "or" e "not" podem vir em qualquer caixa.
                return lexema.toLowerCase(Locale.ROOT);
            case OPERADOR_RELACIONAL:
            case SIMBOLO_ESPECIAL:
            case ATRIBUICAO:
            case FIM:
                return lexema;
            case CONSTANTE_STRING:
                return Terminal.CONSTANTE_STRING;
            case CONSTANTE_CHAR:
                return Terminal.CONSTANTE_CHAR;
            case ERRO_LEXICO:
            default:
                return null;
        }
    }

    // Todos os tokens lidos ate agora, inclusive os erros (para a aba de tokens).
    public List<Token> getTokensLidos() {
        return Collections.unmodifiableList(tokensLidos);
    }

    public List<ErroCompilacao> getErrosLexicos() {
        return Collections.unmodifiableList(errosLexicos);
    }
}
