package minipascal.sintatico.parser;

import minipascal.sintatico.gramatica.Producao;

import java.util.List;

/**
 * Ações executadas pelo analisador sintático junto com a pilha de atributos.
 *
 * Ao lado da pilha de estados, o analisador mantém uma pilha de valores:
 *   - ao empilhar um terminal, empilha o valor devolvido por aoEmpilhar
 *     (por padrão, o próprio Terminal);
 *   - ao reduzir por A -> X1 ... Xn, desempilha os n valores de X1 ... Xn,
 *     chama aoReduzir com eles (na ordem do corpo) e empilha o resultado
 *     como valor de A.
 * O valor final (do símbolo inicial) é devolvido no resultado da análise,
 * desde que não tenha havido erro sintático.
 *
 * Na recuperação de um erro sintático, o analisador pode:
 *   - empilhar um não terminal que não foi de fato reconhecido, com valor
 *     null; por isso aoReduzir pode receber null entre os valores;
 *   - inserir um terminal que falta no texto; aoEmpilhar o recebe com
 *     isInserido() verdadeiro e sem token (getToken() devolve null).
 * As duas coisas só acontecem em programas com erro sintático.
 *
 * É aqui que a análise semântica (Fase 6) vai se encaixar: as verificações
 * acontecem nas reduções.
 */
public interface AcoesReducao {

    // Nenhuma ação: só reconhece a entrada.
    AcoesReducao NENHUMA = (producao, valores, linha) -> null;

    /**
     * @param producao produção pela qual se reduziu
     * @param valores  valores dos símbolos do corpo, na ordem (vazia para ε)
     * @param linha    linha do primeiro token da construção (para ε, a do próximo token)
     * @return valor do não terminal da cabeça
     */
    Object aoReduzir(Producao producao, List<Object> valores, int linha);

    default Object aoEmpilhar(Terminal terminal) {
        return terminal;
    }
}
