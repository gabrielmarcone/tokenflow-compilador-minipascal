package minipascal.semantico;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.Token;
import minipascal.sintatico.parser.ResultadoAnalise;

import java.util.List;

/**
 * Resultado da análise completa de um programa (léxica, sintática e
 * semântica): os erros das três fases, em ordem de linha e, na mesma linha, na
 * ordem do texto; os tokens lidos; e a tabela de símbolos montada.
 *
 * A saída pedida pelo enunciado é a de formatar(): "OK" se o programa estiver
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
     * Falso quando houve erro sintático: a análise semântica parou nele (ver
     * AnalisadorSemantico), e a tabela de símbolos tem só o que foi declarado
     * até ali.
     */
    public boolean isSemanticaCompleta() {
        return semanticaCompleta;
    }

    // "OK", ou um erro por linha.
    public String formatar() {
        return analise.formatar();
    }
}
