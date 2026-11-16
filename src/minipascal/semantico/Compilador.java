package minipascal.semantico;

import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.slr.TabelaSLR;

/**
 * A parte de análise do compilador, as três fases juntas, numa única
 * passada pelo texto:
 *
 *   texto -> AnalisadorLexico -> AdaptadorLexico -> AnalisadorSintatico (SLR(1))
 *                                                       |  a cada redução
 *                                                       v
 *                                                 AnalisadorSemantico
 *                                                 (tabela de símbolos e tipos)
 *
 * O léxico entrega um token por vez ao sintático; o sintático chama as ações
 * semânticas a cada redução. Cada análise usa um AnalisadorSemantico novo
 * (ele guarda a tabela de símbolos daquele programa); as tabelas SLR(1) e a
 * de palavras reservadas são montadas uma única vez.
 */
public final class Compilador {

    private final TabelaSLR tabela;
    private final TabelaPalavrasReservadas palavrasReservadas;

    // Com a gramática completa (gramatica.txt).
    public Compilador() {
        this(AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA),
                new TabelaPalavrasReservadas());
    }

    public Compilador(TabelaSLR tabela, TabelaPalavrasReservadas palavrasReservadas) {
        this.tabela = tabela;
        this.palavrasReservadas = palavrasReservadas;
    }

    public ResultadoCompilacao analisar(String fonte) {
        AnalisadorSemantico semantico = new AnalisadorSemantico(tabela.getGramatica());
        ResultadoAnalise analise = new AnalisadorSintatico(tabela, palavrasReservadas, semantico).analisar(fonte);
        return new ResultadoCompilacao(analise, semantico.getTabela(), !semantico.isInterrompida());
    }
}
