package minipascal.sintatico.parser;

import minipascal.erros.ErroCompilacao;
import minipascal.sintatico.gramatica.Producao;

import java.util.Collections;
import java.util.List;

/**
 * Acoes executadas pelo analisador sintatico junto com a pilha de atributos.
 *
 * Ao lado da pilha de estados, o analisador mantem uma pilha de valores:
 *   - ao empilhar um terminal, empilha o valor devolvido por aoEmpilhar
 *     (por padrao, o proprio Terminal);
 *   - ao reduzir por A -> X1 ... Xn, desempilha os n valores de X1 ... Xn,
 *     chama aoReduzir com eles (na ordem do corpo) e empilha o resultado
 *     como valor de A.
 * O valor final (do simbolo inicial) e devolvido no resultado da analise,
 * desde que nao tenha havido erro sintatico.
 *
 * Na recuperacao de um erro sintatico, o analisador pode:
 *   - empilhar um nao terminal que nao foi de fato reconhecido, com valor
 *     null; por isso aoReduzir pode receber null entre os valores;
 *   - inserir um terminal que falta no texto; aoEmpilhar o recebe com
 *     isInserido() verdadeiro e sem token (getToken() devolve null).
 * As duas coisas so acontecem depois de um erro sintatico, e o analisador
 * avisa o primeiro deles com aoErroSintatico.
 *
 * E assim que a analise semantica (minipascal.semantico.AnalisadorSemantico)
 * se encaixa: as verificacoes acontecem nas reducoes, e os erros que as acoes
 * encontram (getErros) entram no resultado junto com os lexicos e os
 * sintaticos; o resultado os poe na ordem do texto (ver ResultadoAnalise).
 */
public interface AcoesReducao {

    // Nenhuma acao: so reconhece a entrada.
    AcoesReducao NENHUMA = (producao, valores, linha) -> null;

    /**
     * @param producao producao pela qual se reduziu
     * @param valores  valores dos simbolos do corpo, na ordem (vazia para eps)
     * @param linha    linha do primeiro token da construcao (para eps, a do proximo token)
     * @return valor do nao terminal da cabeca
     */
    Object aoReduzir(Producao producao, List<Object> valores, int linha);

    default Object aoEmpilhar(Terminal terminal) {
        return terminal;
    }

    /**
     * Chamado a cada erro sintatico, antes da recuperacao. Depois dele, a
     * pilha de valores pode receber null e terminais inseridos (ver acima).
     *
     * @param encontrado o terminal com erro
     */
    default void aoErroSintatico(Terminal encontrado) {
    }

    /**
     * Erros encontrados pelas acoes (por exemplo, os semanticos), com linha e
     * posicao. O analisador os inclui no resultado ao fim da analise.
     */
    default List<ErroCompilacao> getErros() {
        return Collections.emptyList();
    }
}
