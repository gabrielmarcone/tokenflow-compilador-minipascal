package minipascal.sintatico.parser;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.core.AnalisadorLexico;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.gramatica.LeitorGramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.slr.Acao;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analisador sintático SLR(1): o algoritmo empilha/reduz guiado pelas tabelas
 * ACTION e GOTO.
 *
 * Pilhas (sempre do mesmo tamanho):
 *   - estados: estados do autômato LR(0);
 *   - valores: atributos dos símbolos (ver AcoesReducao);
 *   - linhas:  linha do primeiro token de cada símbolo da pilha.
 *
 * A cada passo, com s no topo e a como terminal atual:
 *   ACTION[s, a] = empilhar t  -> empilha t e lê o próximo terminal;
 *   ACTION[s, a] = reduzir A -> β -> desempilha |β| itens, empilha GOTO[topo, A];
 *   ACTION[s, a] = aceitar     -> fim, entrada correta;
 *   célula vazia               -> erro sintático.
 *
 * Nesta etapa a análise para no primeiro erro sintático (a recuperação vem
 * depois). Mesmo assim o resto do texto é lido pelo léxico, para que todos os
 * erros léxicos do arquivo sejam relatados.
 */
public class AnalisadorSintatico {

    // Gramática do subconjunto das expressões (primeira etapa do enunciado).
    public static final String GRAMATICA_EXPRESSOES = "expressoes.txt";

    // Tabelas das gramáticas embutidas, montadas uma única vez por execução.
    private static final Map<String, TabelaSLR> TABELAS = new ConcurrentHashMap<>();

    private final TabelaSLR tabela;
    private final TabelaPalavrasReservadas palavrasReservadas;
    private final AcoesReducao acoes;

    public AnalisadorSintatico(TabelaSLR tabela, TabelaPalavrasReservadas palavrasReservadas, AcoesReducao acoes) {
        this.tabela = tabela;
        this.palavrasReservadas = palavrasReservadas;
        this.acoes = acoes;
    }

    public AnalisadorSintatico(TabelaSLR tabela) {
        this(tabela, new TabelaPalavrasReservadas(), AcoesReducao.NENHUMA);
    }

    // Tabela de uma gramática que acompanha o programa (construída na primeira chamada).
    public static TabelaSLR tabelaEmbutida(String nomeGramatica) {
        return TABELAS.computeIfAbsent(nomeGramatica,
                nome -> TabelaSLR.construir(LeitorGramatica.lerRecurso(nome)));
    }

    public ResultadoAnalise analisar(String fonte) {
        AdaptadorLexico entrada = new AdaptadorLexico(new AnalisadorLexico(fonte, palavrasReservadas));
        List<Integer> estados = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        List<Integer> linhas = new ArrayList<>();
        estados.add(0);
        valores.add(null);
        linhas.add(1);

        List<ErroCompilacao> erros = new ArrayList<>();
        Object valorFinal = null;
        Terminal atual = entrada.proximo();
        // Pilha de estados no momento em que o terminal atual chegou, antes das
        // reduções feitas com ele: é dela que saem os "esperados" de um erro.
        List<Integer> estadosAoLerAtual = new ArrayList<>(estados);

        while (true) {
            Acao acao = tabela.acao(topo(estados), atual.getNome());
            if (acao == null) {
                // Erros léxicos lidos até aqui vêm antes do erro sintático; os que
                // ainda estão no resto do texto vêm depois.
                erros.addAll(entrada.getErrosLexicos());
                erros.add(erroSintatico(estadosAoLerAtual, atual));
                int jaRelatados = entrada.getErrosLexicos().size();
                while (!entrada.proximo().isFimDeArquivo()) {
                    // só lê o resto do texto, para encontrar os erros léxicos
                }
                List<ErroCompilacao> lexicos = entrada.getErrosLexicos();
                erros.addAll(lexicos.subList(jaRelatados, lexicos.size()));
                break;
            }
            if (acao.isEmpilhar()) {
                estados.add(acao.getAlvo());
                valores.add(acoes.aoEmpilhar(atual));
                linhas.add(atual.getLinha());
                atual = entrada.proximo();
                estadosAoLerAtual = new ArrayList<>(estados);
            } else if (acao.isReduzir()) {
                Producao p = tabela.getGramatica().getProducao(acao.getAlvo());
                int n = p.tamanho();
                int base = estados.size() - n;
                int linha = n > 0 ? linhas.get(base) : atual.getLinha();
                List<Object> filhos = new ArrayList<>(valores.subList(base, valores.size()));
                truncar(estados, base);
                truncar(valores, base);
                truncar(linhas, base);
                int destino = tabela.desvio(topo(estados), p.getCabeca());
                if (destino < 0) {
                    // Não acontece com uma tabela SLR construída corretamente.
                    throw new IllegalStateException("GOTO vazio no estado " + topo(estados)
                            + " para " + p.getCabeca());
                }
                estados.add(destino);
                valores.add(acoes.aoReduzir(p, filhos, linha));
                linhas.add(linha);
            } else {
                valorFinal = valores.get(valores.size() - 1);
                erros.addAll(entrada.getErrosLexicos());
                break;
            }
        }
        return new ResultadoAnalise(erros, entrada.getTokensLidos(), valorFinal);
    }

    private ErroCompilacao erroSintatico(List<Integer> estados, Terminal atual) {
        String esperado = DescricaoTerminal.listaDeEsperados(terminaisValidos(estados));
        String mensagem = atual.isFimDeArquivo()
                ? "Fim do arquivo inesperado; esperado " + esperado
                : "Encontrado " + DescricaoTerminal.encontrado(atual) + "; esperado " + esperado;
        return new ErroCompilacao(CategoriaErro.SINTATICO, atual.getLinha(), mensagem);
    }

    /*
     * Terminais que de fato continuariam a entrada a partir desta pilha.
     *
     * A pilha usada é a do momento em que o token com erro chegou. O SLR pode
     * reduzir antes de detectar o erro (em "a < b < c", o "b" vira SIEXPR antes
     * de o segundo '<' ser recusado); olhar a pilha depois dessas reduções
     * esconderia opções que valiam logo após o "b", como '*'.
     *
     * Num estado SLR, as reduções valem para todo o FOLLOW da cabeça, que pode
     * incluir terminais impossíveis neste ponto (ex.: ')' depois de "a" numa
     * expressão sem parêntese aberto). Por isso cada candidato é simulado numa
     * cópia da pilha: só fica na lista se, depois das reduções, ele chega a
     * ser empilhado (ou aceito, no caso de '$').
     */
    List<String> terminaisValidos(List<Integer> estados) {
        List<String> candidatos = tabela.terminaisEsperados(topo(estados));
        List<String> validos = new ArrayList<>();
        for (String t : candidatos) {
            if (seriaConsumido(estados, t)) {
                validos.add(t);
            }
        }
        return validos.isEmpty() ? candidatos : validos;
    }

    private boolean seriaConsumido(List<Integer> estadosOriginais, String terminal) {
        List<Integer> pilha = new ArrayList<>(estadosOriginais);
        // cada redução encolhe ou mantém a pilha; o limite só protege contra laço
        for (int passos = 0; passos < 100000; passos++) {
            Acao a = tabela.acao(topo(pilha), terminal);
            if (a == null) {
                return false;
            }
            if (a.isEmpilhar() || a.isAceitar()) {
                return true;
            }
            Producao p = tabela.getGramatica().getProducao(a.getAlvo());
            truncar(pilha, pilha.size() - p.tamanho());
            int destino = tabela.desvio(topo(pilha), p.getCabeca());
            if (destino < 0) {
                return false;
            }
            pilha.add(destino);
        }
        return false;
    }

    public TabelaSLR getTabela() {
        return tabela;
    }

    private static int topo(List<Integer> pilha) {
        return pilha.get(pilha.size() - 1);
    }

    private static void truncar(List<?> lista, int tamanho) {
        lista.subList(tamanho, lista.size()).clear();
    }
}
