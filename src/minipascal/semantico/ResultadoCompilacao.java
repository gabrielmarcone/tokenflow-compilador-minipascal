package minipascal.semantico;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.Token;
import minipascal.sintatico.parser.ResultadoAnalise;

import java.util.List;

/**
 * Resultado da analise completa de um programa (lexica, sintatica e
 * semantica): os erros das tres fases, em ordem de linha e, na mesma linha, na
 * ordem do texto; os tokens lidos; e a tabela de simbolos montada.
 *
 * A saida pedida pelo enunciado e a de formatar(): "OK" se o programa estiver
 * correto, ou um erro por linha, com o tipo do erro e a linha.
 */
public final class ResultadoCompilacao {

    private final ResultadoAnalise analise;
    private final TabelaSimbolos tabela;
    private final boolean semanticaCompleta;

    ResultadoCompilacao(ResultadoAnalise analise, TabelaSimbolos tabela, boolean semanticaCompleta) {
        this.analise = analise;
        this.tabela = tabela;
        this.semanticaCompleta = semanticaCompleta;
    }

    public boolean isSucesso() {
        return analise.isSucesso();
    }

    public List<ErroCompilacao> getErros() {
        return analise.getErros();
    }

    public List<ErroCompilacao> errosDa(CategoriaErro categoria) {
        return analise.errosDa(categoria);
    }

    public List<Token> getTokens() {
        return analise.getTokens();
    }

    public TabelaSimbolos getTabelaSimbolos() {
        return tabela;
    }

    /**
     * Falso quando houve erro sintatico: a analise semantica parou nele (ver
     * AnalisadorSemantico), e a tabela de simbolos tem so o que foi declarado
     * ate ali.
     */
    public boolean isSemanticaCompleta() {
        return semanticaCompleta;
    }

    // "OK", ou um erro por linha.
    public String formatar() {
        return analise.formatar();
    }
}
