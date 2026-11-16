package minipascal.semantico;

import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.OuvinteAnalise;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.slr.TabelaSLR;

/**
 * A parte de analise do compilador, as tres fases juntas, numa unica
 * passada pelo texto:
 *
 *   texto -> AnalisadorLexico -> AdaptadorLexico -> AnalisadorSintatico (SLR(1))
 *                                                       |  a cada reducao
 *                                                       v
 *                                                 AnalisadorSemantico
 *                                                 (tabela de simbolos e tipos)
 *
 * O lexico entrega um token por vez ao sintatico; o sintatico chama as acoes
 * semanticas a cada reducao. Cada analise usa um AnalisadorSemantico novo
 * (ele guarda a tabela de simbolos daquele programa); as tabelas SLR(1) e a
 * de palavras reservadas sao montadas uma unica vez.
 */
public final class Compilador {

    private final TabelaSLR tabela;
    private final TabelaPalavrasReservadas palavrasReservadas;

    // Com a gramatica completa (gramatica.txt).
    public Compilador() {
        this(AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA),
                new TabelaPalavrasReservadas());
    }

    public Compilador(TabelaSLR tabela, TabelaPalavrasReservadas palavrasReservadas) {
        this.tabela = tabela;
        this.palavrasReservadas = palavrasReservadas;
    }

    public ResultadoCompilacao analisar(String fonte) {
        return analisar(fonte, null);
    }

    /**
     * Analisa avisando cada passo da analise sintatica ao ouvinte (por
     * exemplo, um RastroAnalise, para mostrar os movimentos da pilha).
     *
     * @param ouvinte pode ser null
     */
    public ResultadoCompilacao analisar(String fonte, OuvinteAnalise ouvinte) {
        AnalisadorSemantico semantico = new AnalisadorSemantico(tabela.getGramatica());
        ResultadoAnalise analise = new AnalisadorSintatico(tabela, palavrasReservadas, semantico)
                .analisar(fonte, ouvinte);
        return new ResultadoCompilacao(analise, semantico.getTabela(), !semantico.isInterrompida());
    }

    // A tabela SLR(1) usada (gramatica, estados, ACTION e GOTO).
    public TabelaSLR getTabelaSLR() {
        return tabela;
    }
}
