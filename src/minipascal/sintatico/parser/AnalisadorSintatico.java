package minipascal.sintatico.parser;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.core.AnalisadorLexico;
import minipascal.lexico.model.TipoToken;
import minipascal.lexico.model.Token;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.gramatica.LeitorGramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.slr.Acao;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analisador sintatico SLR(1): o algoritmo empilha/reduz guiado pelas
 * tabelas ACTION e GOTO (livro do Dragao, secao 4.6).
 *
 * Pilhas, sempre do mesmo tamanho: estados (do automato LR(0)), valores
 * (atributos dos simbolos, ver AcoesReducao) e linhas (linha do primeiro
 * token de cada simbolo). A cada passo, com s no topo e a como terminal:
 *   ACTION[s, a] = empilhar t      -> empilha t e le o proximo terminal;
 *   ACTION[s, a] = reduzir A -> b  -> desempilha |b| itens, empilha GOTO[topo, A];
 *   ACTION[s, a] = aceitar         -> fim, entrada correta;
 *   celula vazia                   -> erro sintatico.
 *
 * Num erro, o analisador relata e se recupera (ver recuperar), para mostrar
 * numa analise so todos os erros do programa, como pede o enunciado:
 *   - modo panico (secao 4.8.3): volta na pilha ate um estado com GOTO para
 *     algum nao terminal A, descarta tokens e segue como se A tivesse sido
 *     reconhecido;
 *   - insercao: insere o terminal que falta (';', 'then', ')'...).
 * Cada retomada possivel e simulada sobre os tokens seguintes; vale a que
 * descarta menos tokens e ainda empilha pelo menos MINIMO_PARA_RETOMAR tokens
 * sem novo erro (no empate, a que vai mais longe; depois, o modo panico).
 * Assim um erro que e so consequencia do anterior nao e relatado.
 *
 * Os erros lexicos (do adaptador) e os das acoes (os semanticos) entram
 * junto; ResultadoAnalise ordena tudo por linha e posicao no texto.
 */
public class AnalisadorSintatico {

    // Gramatica completa do Mini_Pascal (programas inteiros).
    public static final String GRAMATICA_COMPLETA = "gramatica.txt";

    // Gramatica do subconjunto das expressoes (primeira etapa do enunciado).
    public static final String GRAMATICA_EXPRESSOES = "expressoes.txt";

    // Tokens do texto que uma retomada precisa empilhar (na simulacao) para ser
    // escolhida; dois erros a menos disso um do outro contam como um so. O valor
    // foi escolhido medindo, em milhares de programas com erros inseridos de
    // proposito, quantos erros sao encontrados e quantos erros falsos aparecem,
    // e conferindo os exemplos do enunciado: com 3 (o valor do yacc), uma
    // construcao que nao existe no Mini_Pascal, como o for do enunciado, gera
    // um segundo erro em cascata (no 'to'); com 5, erros em linhas vizinhas
    // deixam de ser encontrados.
    static final int MINIMO_PARA_RETOMAR = 4;

    // Ate quantos tokens a frente cada retomada candidata e simulada.
    static final int ALCANCE_DA_SIMULACAO = 500;

    // Tabelas das gramaticas embutidas, montadas uma unica vez por execucao.
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

    // Tabela de uma gramatica que acompanha o programa (construida na primeira chamada).
    public static TabelaSLR tabelaEmbutida(String nomeGramatica) {
        return TABELAS.computeIfAbsent(nomeGramatica,
                nome -> TabelaSLR.construir(LeitorGramatica.lerRecurso(nome)));
    }

    public ResultadoAnalise analisar(String fonte) {
        return analisar(fonte, null);
    }

    /**
     * Analisa avisando cada passo ao ouvinte (por exemplo, um RastroAnalise,
     * para mostrar os movimentos da pilha). O ouvinte so observa: o resultado
     * e o mesmo de analisar(fonte).
     *
     * @param ouvinte pode ser null
     */
    public ResultadoAnalise analisar(String fonte, OuvinteAnalise ouvinte) {
        AdaptadorLexico adaptador = new AdaptadorLexico(new AnalisadorLexico(fonte, palavrasReservadas));
        Entrada entrada = new Entrada(adaptador);
        List<Integer> estados = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        List<Integer> linhas = new ArrayList<>();
        estados.add(0);
        valores.add(null);
        linhas.add(1);

        // Erros de todas as fases, na ordem em que aparecem; ResultadoAnalise ordena.
        List<ErroCompilacao> erros = new ArrayList<>();
        int lexicosIncluidos = 0;
        boolean houveErroSintatico = false;
        Object valorFinal = null;
        Terminal atual = entrada.consumir();
        // Pilha de estados no momento em que o terminal atual chegou, antes das
        // reducoes feitas com ele: e dela que saem os "esperados" de um erro.
        List<Integer> estadosAoLerAtual = new ArrayList<>(estados);

        while (true) {
            // Consulta ACTION[estado do topo, terminal atual].
            Acao acao = tabela.acao(topo(estados), atual.getNome());
            if (acao == null) {
                // Erros lexicos anteriores a este token vem antes deste erro.
                lexicosIncluidos = incluirLexicos(adaptador, erros, lexicosIncluidos, atual.getPosicao());
                ErroCompilacao erro = erroSintatico(estadosAoLerAtual, atual, adaptador);
                erros.add(erro);
                houveErroSintatico = true;
                acoes.aoErroSintatico(atual);
                if (ouvinte != null) {
                    ouvinte.erro(Collections.unmodifiableList(estados), atual, erro);
                }
                atual = recuperar(estados, valores, linhas, atual, entrada, ouvinte);
                estadosAoLerAtual = new ArrayList<>(estados);
                continue;
            }
            if (ouvinte != null) {
                ouvinte.acao(Collections.unmodifiableList(estados), atual, acao);
            }
            // Empilhar: o terminal entra na pilha com o estado de destino; le o proximo.
            if (acao.isEmpilhar()) {
                estados.add(acao.getAlvo());
                valores.add(acoes.aoEmpilhar(atual));
                linhas.add(atual.getLinha());
                atual = entrada.consumir();
                estadosAoLerAtual = new ArrayList<>(estados);
            } else if (acao.isReduzir()) {
                // Reduzir A -> beta: tira |beta| itens das pilhas; a acao semantica recebe
                // os valores deles e o resultado entra com o estado GOTO[novo topo, A].
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
                    // Nao acontece com uma tabela SLR construida corretamente.
                    throw new IllegalStateException("GOTO vazio no estado " + topo(estados)
                            + " para " + p.getCabeca());
                }
                estados.add(destino);
                valores.add(acoes.aoReduzir(p, filhos, linha));
                linhas.add(linha);
            } else {
                // Aceitar so acontece no fim do arquivo: todo o texto passou pelo lexico.
                // Com erro sintatico, o valor montado pelas acoes nao representa o programa.
                valorFinal = houveErroSintatico ? null : valores.get(valores.size() - 1);
                break;
            }
        }
        incluirLexicos(adaptador, erros, lexicosIncluidos, Integer.MAX_VALUE);
        // Os erros das acoes (os semanticos); o resultado ordena tudo por linha e posicao.
        erros.addAll(acoes.getErros());
        return new ResultadoAnalise(erros, adaptador.getTokensLidos(), valorFinal);
    }

    /*
     * Recuperacao. Para k = 0, 1, 2... tokens descartados a partir do token com
     * erro, avalia dois tipos de retomada:
     *   - modo panico: para cada estado s da pilha (do topo para a base) e cada
     *     nao terminal A com GOTO[s, A] definido, corta a pilha acima de s e
     *     empilha GOTO[s, A], como se A tivesse sido reconhecido;
     *   - insercao: mantem a pilha e insere um terminal que falta (um 'begin',
     *     um ';', um ')'...) antes do token de retomada.
     * Cada candidata e simulada adiante; vale a regra descrita no comentario da
     * classe. O terminal devolvido (o inserido ou o de retomada) sera empilhado
     * em seguida, e a retomada escolhida empilha pelo menos MINIMO_PARA_RETOMAR
     * tokens do texto sem erro (a analise real repete a simulacao), entao a
     * analise sempre avanca. No fim do arquivo sempre ha retomada: a candidata
     * da base da pilha, GOTO[0, S], aceita o '$'.
     */
    private Terminal recuperar(List<Integer> estados, List<Object> valores, List<Integer> linhas,
                               Terminal erro, Entrada entrada, OuvinteAnalise ouvinte) {
        entrada.devolver(erro);
        // Primeiro tenta sem descartar nada; depois descartando 1, 2, 3... tokens.
        for (int descartados = 0; ; descartados++) {
            Terminal retomada = entrada.ver(descartados);
            int melhorAlcance = 0;
            int melhorNivel = -1;
            int melhorDestino = -1;
            String melhorNaoTerminal = null;
            String melhorInsercao = null;

            // Modo panico: no empate, vale a mais proxima do topo (a primeira encontrada).
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
                        melhorNaoTerminal = naoTerminal;
                    }
                    if (melhorAlcance >= ALCANCE_DA_SIMULACAO) {
                        break pesquisa; // nenhuma outra candidata pode ir mais longe
                    }
                }
            }
            // Insercao: so vale se for estritamente mais longe que o modo panico.
            // ('$' nunca e inserido; um terminal sem acao no topo daria alcance 0.)
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

            // Achou uma retomada boa: aplica de verdade (descarta, insere ou desempilha).
            if (melhorAlcance >= MINIMO_PARA_RETOMAR) { // ATE_O_FIM tambem conta
                StringBuilder descricao = new StringBuilder();
                if (ouvinte != null && descartados > 0) {
                    List<String> lexemas = new ArrayList<>();
                    for (int i = 0; i < descartados; i++) {
                        lexemas.add("'" + entrada.ver(i).getLexema() + "'");
                    }
                    descricao.append("descarta ").append(String.join(", ", lexemas)).append("; ");
                }
                for (int i = 0; i < descartados; i++) {
                    entrada.consumir();
                }
                if (melhorInsercao != null) {
                    Terminal inserido = Terminal.inserido(melhorInsercao, retomada.getLinha());
                    if (ouvinte != null) {
                        descricao.append("insere '").append(melhorInsercao).append("', que faltava");
                        ouvinte.recuperacao(Collections.unmodifiableList(estados), inserido, retomada.getPosicao(),
                                descricao.toString());
                    }
                    return inserido;
                }
                int desempilhados = estados.size() - (melhorNivel + 1);
                truncar(estados, melhorNivel + 1);
                truncar(valores, melhorNivel + 1);
                truncar(linhas, melhorNivel + 1);
                estados.add(melhorDestino);
                valores.add(null);
                linhas.add(retomada.getLinha());
                Terminal proximo = entrada.consumir();
                if (ouvinte != null) {
                    if (desempilhados > 0) {
                        descricao.append("desempilha ").append(desempilhados)
                                .append(desempilhados == 1 ? " estado, " : " estados, ");
                    }
                    descricao.append("considera ").append(melhorNaoTerminal).append(" reconhecido e vai para o estado ")
                            .append(melhorDestino);
                    ouvinte.recuperacao(Collections.unmodifiableList(estados), proximo, proximo.getPosicao(),
                            descricao.toString());
                }
                return proximo;
            }
            if (retomada.isFimDeArquivo()) {
                // Nao acontece: no fim do arquivo, a candidata da base da pilha
                // (GOTO[0, S], S o simbolo inicial) sempre aceita.
                throw new IllegalStateException("recuperação sem retomada no fim do arquivo");
            }
        }
    }

    // Valor de "alcance" de uma simulacao que aceita o programa ate o fim.
    private static final int ATE_O_FIM = Integer.MAX_VALUE;

    /*
     * Simula a analise (so a pilha de estados) a partir da pilha formada pelos
     * primeiros "altura" estados de "estados" mais, se "destino" >= 0, o estado
     * "destino" no topo: primeiro o terminal inserido, se houver, depois os
     * tokens a partir de ver(inicio). A pilha real nao e copiada nem alterada
     * (o que a simulacao empilha fica numa lista a parte; o que ela desempilha
     * abaixo disso so baixa a altura), para que o custo de cada candidata nao
     * dependa da profundidade da pilha.
     *
     * Devolve quantos tokens da entrada foram empilhados antes de um erro, ate
     * ALCANCE_DA_SIMULACAO, ou ATE_O_FIM se a entrada for aceita (as duas
     * coisas nao acontecem para o mesmo ponto de retomada: aceitar exige
     * empilhar todos os tokens que restam). O terminal inserido nao conta; se
     * ele mesmo nao puder ser empilhado, devolve 0.
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
        // Salvaguarda: entre dois empilhamentos ha sempre um numero finito de reducoes.
        for (int passos = 0; passos < 100000; passos++) {
            int topo = acima.isEmpty() ? estados.get(altura - 1) : acima.get(acima.size() - 1);
            Acao a = tabela.acao(topo, terminal);
            if (a == null) {
                return empilhados;
            }
            if (a.isAceitar()) {
                return ATE_O_FIM; // so no '$', entao o inserido (nunca '$') ja foi empilhado
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

    // Inclui os erros lexicos ainda nao incluidos cujo token vem antes da posicao dada.
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
     * Terminais do adaptador com leitura antecipada: a recuperacao olha os
     * tokens seguintes sem consumi-los.
     */
    private static final class Entrada {
        private final AdaptadorLexico adaptador;
        private final List<Terminal> adiante = new ArrayList<>();

        Entrada(AdaptadorLexico adaptador) {
            this.adaptador = adaptador;
        }

        // i-esimo terminal a frente (0 = o proximo a ser consumido)
        Terminal ver(int i) {
            while (adiante.size() <= i) {
                adiante.add(adaptador.proximo());
            }
            return adiante.get(i);
        }

        Terminal consumir() {
            return adiante.isEmpty() ? adaptador.proximo() : adiante.remove(0);
        }

        // Poe de volta na frente um terminal ja consumido.
        void devolver(Terminal t) {
            adiante.add(0, t);
        }
    }

    private ErroCompilacao erroSintatico(List<Integer> estados, Terminal atual, AdaptadorLexico adaptador) {
        String esperado = DescricaoTerminal.listaDeEsperados(terminaisValidos(estados));
        String mensagem = atual.isFimDeArquivo()
                ? "Fim do arquivo inesperado; esperado " + esperado
                : "Encontrado " + DescricaoTerminal.encontrado(atual) + "; esperado " + esperado;
        // O erro e relatado na linha do simbolo encontrado. Quando ele esta numa
        // linha depois da do token anterior (o caso tipico: falta ';' no fim da
        // linha de cima), a mensagem diz tambem onde fica esse token anterior.
        Token anterior = tokenAnterior(adaptador, atual.getPosicao());
        if (anterior != null && anterior.getLinha() < atual.getLinha()) {
            mensagem += " (depois de '" + anterior.getLexema() + "' na linha " + anterior.getLinha() + ")";
        }
        return new ErroCompilacao(CategoriaErro.SINTATICO, atual.getLinha(), mensagem, atual.getPosicao());
    }

    // Token do texto logo antes da posicao dada, sem contar erros lexicos (que
    // nao chegam a analise sintatica), ou null se nao houver.
    private static Token tokenAnterior(AdaptadorLexico adaptador, int posicao) {
        List<Token> tokens = adaptador.getTokensLidos();
        for (int i = Math.min(posicao, tokens.size()) - 1; i >= 0; i--) {
            if (tokens.get(i).getTipo() != TipoToken.ERRO_LEXICO) {
                return tokens.get(i);
            }
        }
        return null;
    }

    /*
     * Terminais que de fato continuariam a entrada a partir desta pilha.
     *
     * A pilha usada e a do momento em que o token com erro chegou. O SLR pode
     * reduzir antes de detectar o erro (em "a < b < c", o "b" vira SIEXPR antes
     * de o segundo '<' ser recusado); olhar a pilha depois dessas reducoes
     * esconderia opcoes que valiam logo apos o "b", como '*'.
     *
     * Num estado SLR, as reducoes valem para todo o FOLLOW da cabeca, que pode
     * incluir terminais impossiveis neste ponto (ex.: ')' depois de "a" numa
     * expressao sem parentese aberto). Por isso cada candidato e simulado numa
     * copia da pilha: so fica na lista se, depois das reducoes, ele chega a
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
        // cada reducao encolhe ou mantem a pilha; o limite so protege contra laco
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
