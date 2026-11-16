package minipascal.sintatico.parser;

import minipascal.erros.ErroCompilacao;
import minipascal.sintatico.slr.Acao;

import java.util.List;

/**
 * Acompanha a análise sintática passo a passo (por exemplo, para mostrar os
 * movimentos da pilha na interface; ver RastroAnalise). O analisador avisa o
 * ouvinte antes de cada ação da tabela, a cada erro e depois de cada
 * recuperação. O ouvinte só observa: as listas que recebe não podem ser
 * alteradas e não devem ser guardadas (mudam no passo seguinte).
 */
public interface OuvinteAnalise {

    /**
     * Antes de aplicar ACTION[topo, atual]: empilhar, reduzir ou aceitar.
     *
     * @param estados a pilha de estados (a base é o índice 0)
     * @param atual   o terminal de entrada (o próximo a ser lido)
     * @param acao    a ação da tabela
     */
    void acao(List<Integer> estados, Terminal atual, Acao acao);

    /**
     * Um erro sintático: ACTION[topo, atual] está vazia.
     *
     * @param erro o erro que vai para o resultado (mensagem com os esperados)
     */
    void erro(List<Integer> estados, Terminal atual, ErroCompilacao erro);

    /**
     * Depois de uma recuperação de erro.
     *
     * @param estados        a pilha depois da recuperação
     * @param proximo        o terminal com que a análise continua (pode ser inserido)
     * @param posicaoDoTexto posição do próximo token do texto: a de proximo ou,
     *                       se ele for inserido, a do token que vem depois dele
     * @param descricao      o que a recuperação fez (tokens descartados, estado
     *                       retomado, terminal inserido)
     */
    void recuperacao(List<Integer> estados, Terminal proximo, int posicaoDoTexto, String descricao);
}
