package minipascal.sintatico.parser;

import minipascal.erros.ErroCompilacao;
import minipascal.sintatico.slr.Acao;

import java.util.List;

/**
 * Acompanha a analise sintatica passo a passo (por exemplo, para mostrar os
 * movimentos da pilha na interface; ver RastroAnalise). O analisador avisa o
 * ouvinte antes de cada acao da tabela, a cada erro e depois de cada
 * recuperacao. O ouvinte so observa: as listas que recebe nao podem ser
 * alteradas e nao devem ser guardadas (mudam no passo seguinte).
 */
public interface OuvinteAnalise {

    /**
     * Antes de aplicar ACTION[topo, atual]: empilhar, reduzir ou aceitar.
     *
     * @param estados a pilha de estados (a base e o indice 0)
     * @param atual   o terminal de entrada (o proximo a ser lido)
     * @param acao    a acao da tabela
     */
    void acao(List<Integer> estados, Terminal atual, Acao acao);

    /**
     * Um erro sintatico: ACTION[topo, atual] esta vazia.
     *
     * @param erro o erro que vai para o resultado (mensagem com os esperados)
     */
    void erro(List<Integer> estados, Terminal atual, ErroCompilacao erro);

    /**
     * Depois de uma recuperacao de erro.
     *
     * @param estados        a pilha depois da recuperacao
     * @param proximo        o terminal com que a analise continua (pode ser inserido)
     * @param posicaoDoTexto posicao do proximo token do texto: a de proximo ou,
     *                       se ele for inserido, a do token que vem depois dele
     * @param descricao      o que a recuperacao fez (tokens descartados, estado
     *                       retomado, terminal inserido)
     */
    void recuperacao(List<Integer> estados, Terminal proximo, int posicaoDoTexto, String descricao);
}
