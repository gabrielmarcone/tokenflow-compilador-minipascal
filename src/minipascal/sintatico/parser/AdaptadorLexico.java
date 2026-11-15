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
 * Liga o analisador léxico da Unidade I ao analisador sintático. O léxico
 * continua devolvendo pares lexema/classe (Token); o adaptador traduz cada
 * Token para o terminal que a gramática espera:
 *
 *   - integer, real, char, string      -> "tipo"
 *   - demais palavras reservadas       -> a própria palavra, em minúsculas
 *   - identificador                    -> "id"
 *   - número inteiro ou real           -> "numero"
 *   - operadores lógicos e aritméticos -> o próprio lexema, em minúsculas
 *     (mod, and, or, not)
 *   - operadores relacionais, símbolos especiais, ":=" e "." -> o lexema
 *   - constante string / char          -> "constante_string" / "constante_char"
 *   - erro léxico                      -> não vira terminal: é registrado como
 *     erro e o adaptador segue para o próximo token
 *   - fim do texto (null)              -> "$"
 *
 * Assim o analisador léxico não muda e a saída da Unidade I continua igual.
 */
public class AdaptadorLexico {

    private final AnalisadorLexico lexico;
    private final List<Token> tokensLidos = new ArrayList<>();
    private final List<ErroCompilacao> errosLexicos = new ArrayList<>();
    private int ultimaLinha = 1;
    private boolean terminou = false;

    public AdaptadorLexico(AnalisadorLexico lexico) {
        this.lexico = lexico;
    }

    // Devolve o próximo terminal. No fim do texto devolve "$" e continua
    // devolvendo "$" em chamadas seguintes (nunca devolve null).
    public Terminal proximo() {
        while (!terminou) {
            Token token = lexico.proximoToken();
            if (token == null) {
                terminou = true;
                break;
            }
            tokensLidos.add(token);
            ultimaLinha = token.getLinha();

            String nome = nomeDoTerminal(token);
            if (nome != null) {
                return new Terminal(nome, token);
            }
            // Erro léxico: registra e pede o próximo token (modo pânico do léxico).
            errosLexicos.add(new ErroCompilacao(CategoriaErro.LEXICO, token.getLinha(),
                    DescricaoErroLexico.descrever(token) + ": " + token.getLexema()));
        }
        return Terminal.fimDeArquivo(ultimaLinha);
    }

    // Nome do terminal da gramática para um token, ou null se for erro léxico.
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

    // Todos os tokens lidos até agora, inclusive os erros (para a aba de tokens).
    public List<Token> getTokensLidos() {
        return Collections.unmodifiableList(tokensLidos);
    }

    public List<ErroCompilacao> getErrosLexicos() {
        return Collections.unmodifiableList(errosLexicos);
    }
}
