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
 * Recuperação de erros: em vez de parar no primeiro erro, o analisador se
 * recupera e continua, para relatar numa única análise o conjunto dos erros
 * do programa, como pede o enunciado. Dois tipos de retomada são avaliados:
 *
 *   - modo pânico (livro do Dragão, seção 4.8.3): procura na pilha um estado
 *     s com desvio GOTO[s, A] para algum não terminal A, descarta tokens da
 *     entrada até achar um que possa continuar depois de A e empilha
 *     GOTO[s, A], como se A tivesse sido reconhecido;
 *   - inserção: insere um terminal que falta (um 'begin', um 'then', um ';',
 *     um ')'...) e segue com a pilha como está. O terminal inserido não está
 *     no texto (Terminal.isInserido) e não conta como token lido.
 *
 * Como escolher entre as retomadas possíveis: cada candidata é simulada sobre
 * os tokens seguintes, contando quantos ela consegue empilhar antes de um novo
 * erro. Vale a que descarta menos tokens, desde que empilhe pelo menos
 * MINIMO_PARA_RETOMAR tokens (ou aceite o programa até o fim); entre as que
 * descartam o mesmo número, a que vai mais longe. No empate, o modo pânico
 * ganha da inserção (o modo pânico é o método do livro; a inserção entra só
 * quando de fato leva mais longe) e, no modo pânico, vale o estado mais
 * próximo do topo. Duas diferenças em relação ao livro, ambas para evitar
 * erros falsos logo depois da retomada: o token de retomada precisa ser de
 * fato aceito depois de A (e não só estar em FOLLOW(A), que no SLR pode
 * conter terminais impossíveis naquele ponto), e a escolha olha adiante.
 *
 * Erros em cascata (consequências do anterior): como a análise real repete a
 * simulação, depois de uma retomada sempre há pelo menos MINIMO_PARA_RETOMAR
 * tokens do texto empilhados antes de um novo erro. É o efeito da regra do
 * yacc (não relatar erro nos primeiros tokens depois de uma recuperação),
 * obtido de outro jeito: uma retomada que erraria logo adiante simplesmente
 * não é escolhida, e a busca descarta mais tokens. Assim, dois erros a menos
 * de MINIMO_PARA_RETOMAR tokens um do outro contam como um só. A análise
 * sempre avança e termina no fim do arquivo.
 *
 * Todo o texto passa pelo léxico, então todos os erros léxicos são relatados.
 * Os erros saem em ordem de linha e, na mesma linha, na ordem do texto.
 */
public class AnalisadorSintatico {

    // Gramática completa do Mini_Pascal (programas inteiros).
    public static final String GRAMATICA_COMPLETA = "gramatica.txt";

    // Gramática do subconjunto das expressões (primeira etapa do enunciado).
    public static final String GRAMATICA_EXPRESSOES = "expressoes.txt";

    // Tokens do texto que uma retomada precisa empilhar (na simulação) para ser
    // escolhida; dois erros a menos disso um do outro contam como um só. O valor
    // foi escolhido medindo, em milhares de programas com erros inseridos de
    // propósito, quantos erros são encontrados e quantos erros falsos aparecem,
    // e conferindo os exemplos do enunciado: com 3 (o valor do yacc), uma
    // construção que não existe no Mini_Pascal, como o for do enunciado, gera
    // um segundo erro em cascata (no 'to'); com 5, erros em linhas vizinhas
    // deixam de ser encontrados.
    static final int MINIMO_PARA_RETOMAR = 4;

    // Até quantos tokens à frente cada retomada candidata é simulada.
    static final int ALCANCE_DA_SIMULACAO = 500;

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
        AdaptadorLexico adaptador = new AdaptadorLexico(new AnalisadorLexico(fonte, palavrasReservadas));
        Entrada entrada = new Entrada(adaptador);
        List<Integer> estados = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        List<Integer> linhas = new ArrayList<>();
        estados.add(0);
        valores.add(null);
        linhas.add(1);

        List<ErroCompilacao> erros = new ArrayList<>();
        int lexicosIncluidos = 0;
        boolean houveErroSintatico = false;
        Object valorFinal = null;
        Terminal atual = entrada.consumir();
        // Pilha de estados no momento em que o terminal atual chegou, antes das
        // reduções feitas com ele: é dela que saem os "esperados" de um erro.
        List<Integer> estadosAoLerAtual = new ArrayList<>(estados);

        while (true) {
            Acao acao = tabela.acao(topo(estados), atual.getNome());
            if (acao == null) {
                // Erros léxicos anteriores a este token vêm antes deste erro.
                lexicosIncluidos = incluirLexicos(adaptador, erros, lexicosIncluidos, atual.getPosicao());
                erros.add(erroSintatico(estadosAoLerAtual, atual));
                houveErroSintatico = true;
                atual = recuperar(estados, valores, linhas, atual, entrada);
                estadosAoLerAtual = new ArrayList<>(estados);
                continue;
            }
            if (acao.isEmpilhar()) {
                estados.add(acao.getAlvo());
                valores.add(acoes.aoEmpilhar(atual));
                linhas.add(atual.getLinha());
                atual = entrada.consumir();
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
                // Aceitar só acontece no fim do arquivo: todo o texto passou pelo léxico.
                // Com erro sintático, o valor montado pelas ações não representa o programa.
                valorFinal = houveErroSintatico ? null : valores.get(valores.size() - 1);
                break;
            }
        }
        incluirLexicos(adaptador, erros, lexicosIncluidos, Integer.MAX_VALUE);
        return new ResultadoAnalise(erros, adaptador.getTokensLidos(), valorFinal);
    }

    /*
     * Recuperação. Para k = 0, 1, 2... tokens descartados a partir do token com
     * erro, avalia dois tipos de retomada:
     *   - modo pânico: para cada estado s da pilha (do topo para a base) e cada
     *     não terminal A com GOTO[s, A] definido, corta a pilha acima de s e
     *     empilha GOTO[s, A], como se A tivesse sido reconhecido;
     *   - inserção: mantém a pilha e insere um terminal que falta (um 'begin',
     *     um ';', um ')'...) antes do token de retomada.
     * Cada candidata é simulada adiante; vale a regra descrita no comentário da
     * classe. O terminal devolvido (o inserido ou o de retomada) será empilhado
     * em seguida, e a retomada escolhida empilha pelo menos MINIMO_PARA_RETOMAR
     * tokens do texto sem erro (a análise real repete a simulação), então a
     * análise sempre avança. No fim do arquivo sempre há retomada: a candidata
     * da base da pilha, GOTO[0, S], aceita o '$'.
     */
    private Terminal recuperar(List<Integer> estados, List<Object> valores, List<Integer> linhas,
                               Terminal erro, Entrada entrada) {
        entrada.devolver(erro);
        for (int descartados = 0; ; descartados++) {
            Terminal retomada = entrada.ver(descartados);
            int melhorAlcance = 0;
            int melhorNivel = -1;
            int melhorDestino = -1;
            String melhorInsercao = null;

            // Modo pânico: no empate, vale a mais próxima do topo (a primeira encontrada).
            pesquisa:
            for (int nivel = estados.size() - 1; nivel >= 0; nivel--) {
                int estado = estados.get(nivel);
                for (String naoTerminal : tabela.getGramatica().getNaoTerminais()) {
                    int destino = tabela.desvio(estado, naoTerminal);
                    if (destino < 0) {
                        continue;
                    }
                    int alcance = simular(estados, nivel + 1, destino, null, entrada, descartados);
                    if (alcance > melhorAlcance) {
                        melhorAlcance = alcance;
                        melhorNivel = nivel;
                        melhorDestino = destino;
                    }
                    if (melhorAlcance >= ALCANCE_DA_SIMULACAO) {
                        break pesquisa; // nenhuma outra candidata pode ir mais longe
                    }
                }
            }
            // Inserção: só vale se for estritamente mais longe que o modo pânico.
            // ('$' nunca é inserido; um terminal sem ação no topo daria alcance 0.)
            for (String terminal : tabela.getGramatica().getTerminais()) {
                if (terminal.equals(Terminal.FIM_DE_ARQUIVO) || tabela.acao(topo(estados), terminal) == null) {
                    continue;
                }
                if (melhorAlcance >= ALCANCE_DA_SIMULACAO) {
                    break;
                }
                int alcance = simular(estados, estados.size(), -1, terminal, entrada, descartados);
                if (alcance > melhorAlcance) {
                    melhorAlcance = alcance;
                    melhorInsercao = terminal;
                }
            }

            if (melhorAlcance >= MINIMO_PARA_RETOMAR) { // ATE_O_FIM também conta
                for (int i = 0; i < descartados; i++) {
                    entrada.consumir();
                }
                if (melhorInsercao != null) {
                    return Terminal.inserido(melhorInsercao, retomada.getLinha());
                }
                truncar(estados, melhorNivel + 1);
                truncar(valores, melhorNivel + 1);
                truncar(linhas, melhorNivel + 1);
                estados.add(melhorDestino);
                valores.add(null);
                linhas.add(retomada.getLinha());
                return entrada.consumir();
            }
            if (retomada.isFimDeArquivo()) {
                // Não acontece: no fim do arquivo, a candidata da base da pilha
                // (GOTO[0, S], S o símbolo inicial) sempre aceita.
                throw new IllegalStateException("recuperação sem retomada no fim do arquivo");
            }
        }
    }

    // Valor de "alcance" de uma simulação que aceita o programa até o fim.
    private static final int ATE_O_FIM = Integer.MAX_VALUE;

    /*
     * Simula a análise (só a pilha de estados) a partir da pilha formada pelos
     * primeiros "altura" estados de "estados" mais, se "destino" >= 0, o estado
     * "destino" no topo: primeiro o terminal inserido, se houver, depois os
     * tokens a partir de ver(inicio). A pilha real não é copiada nem alterada
     * (o que a simulação empilha fica numa lista à parte; o que ela desempilha
     * abaixo disso só baixa a altura), para que o custo de cada candidata não
     * dependa da profundidade da pilha.
     *
     * Devolve quantos tokens da entrada foram empilhados antes de um erro, até
     * ALCANCE_DA_SIMULACAO, ou ATE_O_FIM se a entrada for aceita (as duas
     * coisas não acontecem para o mesmo ponto de retomada: aceitar exige
     * empilhar todos os tokens que restam). O terminal inserido não conta; se
     * ele mesmo não puder ser empilhado, devolve 0.
     */
    private int simular(List<Integer> estados, int altura, int destino, String inserido,
                        Entrada entrada, int inicio) {
        List<Integer> acima = new ArrayList<>();
        if (destino >= 0) {
            acima.add(destino);
        }
        int empilhados = 0;
        int i = inicio;
        boolean usandoInserido = inserido != null;
        String terminal = usandoInserido ? inserido : entrada.ver(i).getNome();
        // Salvaguarda: entre dois empilhamentos há sempre um número finito de reduções.
        for (int passos = 0; passos < 100000; passos++) {
            int topo = acima.isEmpty() ? estados.get(altura - 1) : acima.get(acima.size() - 1);
            Acao a = tabela.acao(topo, terminal);
            if (a == null) {
                return empilhados;
            }
            if (a.isAceitar()) {
                return ATE_O_FIM; // só no '$', então o inserido (nunca '$') já foi empilhado
            }
            if (a.isEmpilhar()) {
                acima.add(a.getAlvo());
                if (usandoInserido) {
                    usandoInserido = false;
                } else {
                    empilhados++;
                    if (empilhados >= ALCANCE_DA_SIMULACAO) {
                        return empilhados;
                    }
                    i++;
                }
                terminal = entrada.ver(i).getNome();
            } else {
                Producao p = tabela.getGramatica().getProducao(a.getAlvo());
                for (int n = p.tamanho(); n > 0; n--) {
                    if (acima.isEmpty()) {
                        altura--;
                    } else {
                        acima.remove(acima.size() - 1);
                    }
                }
                topo = acima.isEmpty() ? estados.get(altura - 1) : acima.get(acima.size() - 1);
                int desvio = tabela.desvio(topo, p.getCabeca());
                if (desvio < 0) {
                    return empilhados;
                }
                acima.add(desvio);
            }
        }
        return empilhados;
    }

    // Inclui os erros léxicos ainda não incluídos cujo token vem antes da posição dada.
    private static int incluirLexicos(AdaptadorLexico adaptador, List<ErroCompilacao> erros,
                                      int jaIncluidos, int antesDaPosicao) {
        List<ErroCompilacao> lexicos = adaptador.getErrosLexicos();
        int i = jaIncluidos;
        while (i < lexicos.size() && adaptador.posicaoDoErroLexico(i) < antesDaPosicao) {
            erros.add(lexicos.get(i));
            i++;
        }
        return i;
    }

    /*
     * Terminais do adaptador com leitura antecipada: a recuperação olha os
     * tokens seguintes sem consumi-los.
     */
    private static final class Entrada {
        private final AdaptadorLexico adaptador;
        private final List<Terminal> adiante = new ArrayList<>();

        Entrada(AdaptadorLexico adaptador) {
            this.adaptador = adaptador;
        }

        // i-ésimo terminal à frente (0 = o próximo a ser consumido)
        Terminal ver(int i) {
            while (adiante.size() <= i) {
                adiante.add(adaptador.proximo());
            }
            return adiante.get(i);
        }

        Terminal consumir() {
            return adiante.isEmpty() ? adaptador.proximo() : adiante.remove(0);
        }

        // Põe de volta na frente um terminal já consumido.
        void devolver(Terminal t) {
            adiante.add(0, t);
        }
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
