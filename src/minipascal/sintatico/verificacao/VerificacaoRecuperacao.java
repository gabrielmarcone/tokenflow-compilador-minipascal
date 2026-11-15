package minipascal.sintatico.verificacao;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.TipoToken;
import minipascal.lexico.model.Token;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.parser.AcoesReducao;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.parser.Terminal;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Verifica a recuperação de erros do analisador sintático: uma análise relata
 * o conjunto dos erros do programa (e não só o primeiro), sem erros em cascata.
 *
 *   - vários erros independentes, cada um com sua linha;
 *   - um erro só não gera outros (inserção de 'then', 'do', 'begin', ';',
 *     ')'... e descarte de tokens);
 *   - contra cascata: uma retomada só vale se empilhar 4 tokens sem erro, então
 *     um erro a 3 tokens da retomada não é relatado (a recuperação descarta
 *     mais tokens); a 4 tokens, é;
 *   - ordem dos erros léxicos e sintáticos na mesma linha;
 *   - o que as ações semânticas recebem durante a recuperação (terminais
 *     inseridos, valores null) e o valor final (null com erro sintático);
 *   - a gramática das expressões também se recupera;
 *   - robustez: textos aleatórios terminam sem exceção, com os erros em ordem
 *     e saída determinística; textos patológicos terminam em tempo razoável.
 *
 * O primeiro erro de cada programa é exatamente o da análise sem recuperação
 * (conferido contra o reconhecedor de Earley; ver VerificacaoParserCompleto).
 */
public class VerificacaoRecuperacao {

    private static int total = 0;
    private static int falhas = 0;

    private static TabelaSLR TABELA;
    private static AnalisadorSintatico ANALISADOR;

    private static final String NUMERO_ETC = "identificador, '(', número, constante string ou constante char";
    private static final String OPERADORES = "'=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div' ou 'mod'";

    public static void main(String[] args) {
        try {
            TABELA = AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA);
            ANALISADOR = new AnalisadorSintatico(TABELA);
        } catch (RuntimeException e) {
            checar("gramatica.txt gera a tabela SLR(1): " + e.getMessage(), false);
            terminar();
        }
        executar("vários erros", VerificacaoRecuperacao::verificarVariosErros);
        executar("um erro só", VerificacaoRecuperacao::verificarUmErroSo);
        executar("contra cascata", VerificacaoRecuperacao::verificarJanela);
        executar("ordem dos erros", VerificacaoRecuperacao::verificarOrdem);
        executar("ações semânticas", VerificacaoRecuperacao::verificarAcoes);
        executar("terminais", VerificacaoRecuperacao::verificarTerminais);
        executar("expressões", VerificacaoRecuperacao::verificarExpressoes);
        executar("robustez", VerificacaoRecuperacao::verificarRobustez);
        executar("desempenho", VerificacaoRecuperacao::verificarDesempenho);
        terminar();
    }

    // ------------------------------------------------------------ vários erros

    private static void verificarVariosErros() {
        checarSaida("três erros independentes, um em cada linha", linhas(
                "program p;",
                "var x: integer;",
                "begin",
                "  x := 1 +;",
                "  x := 2;",
                "  y := (3;",
                "  x := 4;",
                "  if x then x := 5 else x := 6 else x := 7;",
                "  z := 8;",
                "end."),
                "Erro sintático na linha 4: Encontrado ';'; esperado " + NUMERO_ETC,
                "Erro sintático na linha 6: Encontrado ';'; esperado ')', " + OPERADORES,
                "Erro sintático na linha 8: Encontrado 'else'; esperado ';', " + OPERADORES);
        checarSaida("falta ';' depois do nome e falta o '.' final",
                "program p begin x := 1; end",
                "Erro sintático na linha 1: Encontrado 'begin'; esperado ';'",
                "Erro sintático na linha 1: Fim do arquivo inesperado; esperado '.'");
        checarSaida("dois erros na mesma linha", "program p; begin x := 1 + ; y := 2 + ; end.",
                "Erro sintático na linha 1: Encontrado ';'; esperado " + NUMERO_ETC,
                "Erro sintático na linha 1: Encontrado ';'; esperado " + NUMERO_ETC);
        checarSaida("falta 'then' e falta o comando depois do else", linhas(
                "program p;",
                "begin",
                "  if x > 1 x := 2;",
                "  if x > 1 then x := 3 else",
                "end."),
                "Erro sintático na linha 3: Encontrado identificador 'x'; esperado 'then', '+', '-', '*', 'div' ou 'mod'",
                "Erro sintático na linha 5: Encontrado 'end'; esperado identificador, 'begin', 'if' ou 'while'");
        checarSaida("chamadas: falta ')' e falta ','", linhas(
                "program p;",
                "begin",
                "  q(1, 2;",
                "  x := 1;",
                "  q(3 4);",
                "end."),
                "Erro sintático na linha 3: Encontrado ';'; esperado ',', ')', " + OPERADORES,
                "Erro sintático na linha 5: Encontrado número '4'; esperado ',', ')', " + OPERADORES);
        checarSaida("expressão incompleta, ';' faltando antes de 'end' e ')' faltando", linhas(
                "program p;",
                "begin",
                "  x := 1 + 2 * ;",
                "  while x do begin y := 1 end;",
                "  z := (1 + 2;",
                "end."),
                "Erro sintático na linha 3: Encontrado ';'; esperado " + NUMERO_ETC,
                "Erro sintático na linha 4: Encontrado 'end'; esperado ';', " + OPERADORES,
                "Erro sintático na linha 5: Encontrado ';'; esperado ')', " + OPERADORES);
        checarSaida("erros na declaração e no corpo de um procedimento", linhas(
                "program p;",
                "var a, b: integer;",
                "procedure q(var x: integer; y: real);",
                "var t: integer",
                "begin",
                "  t := x;",
                "  x := y",
                "end;",
                "begin",
                "  q(a, b);",
                "end."),
                "Erro sintático na linha 5: Encontrado 'begin'; esperado ';'",
                "Erro sintático na linha 8: Encontrado 'end'; esperado ';', " + OPERADORES);
    }

    // -------------------------------------------------------------- um erro só

    private static void verificarUmErroSo() {
        checarSaida("falta 'then' (o resto do programa segue normal)", linhas(
                "program p;", "begin", "  if x > 1 x := 2;", "  y := 3;", "end."),
                "Erro sintático na linha 3: Encontrado identificador 'x'; esperado 'then', '+', '-', '*', 'div' ou 'mod'");
        checarSaida("falta 'do'", linhas(
                "program p;", "begin", "  while x < 10 x := x + 1;", "  y := 3;", "end."),
                "Erro sintático na linha 3: Encontrado identificador 'x'; esperado 'do', '+', '-', '*', 'div' ou 'mod'");
        checarSaida("falta 'begin' depois das variáveis", linhas(
                "program p;", "var x: integer;", "  x := 1;", "  y := 2;", "end."),
                "Erro sintático na linha 3: Encontrado ':='; esperado ':' ou ','");
        checarSaida("falta ';' entre comandos", linhas(
                "program p;", "begin", "  x := 1", "  y := 2;", "  z := 3;", "end."),
                "Erro sintático na linha 4: Encontrado identificador 'y'; esperado ';', " + OPERADORES);
        checarSaida("falta ')' numa chamada", linhas(
                "program p;", "begin", "  q(1, 2;", "  y := 3;", "  z := 4;", "end."),
                "Erro sintático na linha 3: Encontrado ';'; esperado ',', ')', " + OPERADORES);
        checarSaida("falta ':' numa declaração", linhas(
                "program p;", "var x integer;", "begin", "  x := 1;", "end."),
                "Erro sintático na linha 2: Encontrado tipo 'integer'; esperado ':' ou ','");
        checarSaida("falta ':' num parâmetro", linhas(
                "program p;", "procedure q(a: integer; b integer);", "begin", "  a := b;", "end;",
                "begin", "  q(1, 2);", "end."),
                "Erro sintático na linha 2: Encontrado tipo 'integer'; esperado ':' ou ','");
        checarSaida("falta o '.' final", linhas(
                "program p;", "begin", "  x := 1;", "  y := 2;", "end"),
                "Erro sintático na linha 5: Fim do arquivo inesperado; esperado '.'");
        checarSaida("arquivo termina no meio do programa", linhas("program p;", "begin", "  x := 1;"),
                "Erro sintático na linha 3: Fim do arquivo inesperado; esperado identificador, 'begin', 'end', 'if' ou 'while'");
        checarSaida("não começa com 'program'", "x := 1;",
                "Erro sintático na linha 1: Encontrado identificador 'x'; esperado 'program'");
        checarSaida("só 'begin end'", "begin end",
                "Erro sintático na linha 1: Encontrado 'begin'; esperado 'program'");
        checarSaida("comando 'for' (não existe no Mini_Pascal): um erro só, sem cascata no 'to'",
                "program p; begin for i := 1 to 10 do x := i; end.",
                "Erro sintático na linha 1: Encontrado 'for'; esperado identificador, 'begin', 'if' ou 'while'");
        checarSaida("texto depois do '.' final", "program p; begin x := 1; end. x y z",
                "Erro sintático na linha 1: Encontrado identificador 'x'; esperado fim do arquivo");
        checarSaida("muitos tokens inválidos seguidos: um erro só", "program p; begin x := 1 2 3 4 5 6 7 8 9; end.",
                "Erro sintático na linha 1: Encontrado número '2'; esperado ';', " + OPERADORES);
    }

    // ----------------------------------------------------- janela contra cascata

    private static void verificarJanela() {
        // Retomando no ';', só 3 tokens (';', 'y', ':=') seriam empilhados antes do
        // novo erro: essa retomada não vale, a recuperação descarta mais tokens e o
        // segundo erro (tão perto que provavelmente é consequência do primeiro) não
        // é relatado.
        checarSaida("novo erro a 3 tokens da retomada: não é relatado", "program p; begin x := 1 + ; y := ; end.",
                "Erro sintático na linha 1: Encontrado ';'; esperado " + NUMERO_ETC);
        // Com 4 tokens (';', 'y', ':=', '2'), a retomada vale e o novo erro é relatado.
        checarSaida("novo erro a 4 tokens da retomada: é relatado", "program p; begin x := 1 + ; y := 2 end.",
                "Erro sintático na linha 1: Encontrado ';'; esperado " + NUMERO_ETC,
                "Erro sintático na linha 1: Encontrado 'end'; esperado ';', " + OPERADORES);
        checarSaida("novo erro a 4 tokens (sinal unário): é relatado", "program p; begin x := 1 + ; y := + ; end.",
                "Erro sintático na linha 1: Encontrado ';'; esperado " + NUMERO_ETC,
                "Erro sintático na linha 1: Encontrado ';'; esperado " + NUMERO_ETC);
        // No '*', descartá-lo (modo pânico) e inserir um 'begin' depois dele vão
        // igualmente longe (até o erro no '('). No empate vale o modo pânico; com a
        // inserção, sobraria um bloco aberto e um erro falso no '.' final.
        checarSaida("empate entre modo pânico e inserção: vale o modo pânico (sem erro falso no fim)", linhas(
                "program p;", "var soma: real;", "* begin", "  x := 3.5;", "  (1);", "end."),
                "Erro sintático na linha 3: Encontrado '*'; esperado identificador, 'begin', 'var' ou 'procedure'",
                "Erro sintático na linha 5: Encontrado '('; esperado identificador, 'begin', 'end', 'if' ou 'while'");
        // Um erro a cada 50 linhas, em 2000 linhas: os 40 são relatados, nas linhas certas.
        StringBuilder sb = new StringBuilder("program p;\nbegin\n");
        List<Integer> esperadas = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            if (i % 50 == 0) {
                sb.append("x := x + ;\n");
                esperadas.add(i + 3);
            } else {
                sb.append("x := x + ").append(i).append(";\n");
            }
        }
        sb.append("end.\n");
        List<Integer> obtidas = linhasDosErros(ANALISADOR.analisar(sb.toString()), CategoriaErro.SINTATICO);
        checar("40 erros espalhados em 2000 linhas: todos relatados, nas linhas certas"
                + (obtidas.equals(esperadas) ? "" : " (obtido: " + obtidas + ")"), obtidas.equals(esperadas));
    }

    // ---------------------------------------------------------- ordem dos erros

    private static void verificarOrdem() {
        checarSaida("erro léxico antes do sintático na mesma linha", linhas(
                "program p;", "begin", "  x := @ 1 +;", "  y := 2;", "end."),
                "Erro léxico na linha 3: Caractere inválido: @",
                "Erro sintático na linha 3: Encontrado ';'; esperado " + NUMERO_ETC);
        checarSaida("erro sintático antes do léxico na mesma linha", linhas(
                "program p;", "begin", "  x := 1 +; @", "end."),
                "Erro sintático na linha 3: Encontrado ';'; esperado " + NUMERO_ETC,
                "Erro léxico na linha 3: Caractere inválido: @");
        checarSaida("erro léxico depois do '.' final, antes do texto que sobra", "program p; begin x := 1; end. @ x",
                "Erro léxico na linha 1: Caractere inválido: @",
                "Erro sintático na linha 1: Encontrado identificador 'x'; esperado fim do arquivo");
        checarSaida("erros léxicos em texto descartado pela recuperação também são relatados", linhas(
                "program p;", "begin", "  x := 1 2 @ 3 $ 4;", "  y := 2;", "end."),
                "Erro sintático na linha 3: Encontrado número '2'; esperado ';', " + OPERADORES,
                "Erro léxico na linha 3: Caractere inválido: @",
                "Erro léxico na linha 3: Caractere inválido: $");
    }

    // --------------------------------------------------------- ações semânticas

    // Registra o que a análise passa às ações: terminais inseridos e valores null.
    private static final class AcoesRegistro implements AcoesReducao {
        final List<String> inseridos = new ArrayList<>();
        int filhosNulos = 0;
        int reducoes = 0;

        @Override
        public Object aoEmpilhar(Terminal terminal) {
            if (terminal.isInserido()) {
                inseridos.add(terminal.getNome() + "@" + terminal.getLinha()
                        + (terminal.getToken() == null ? "" : "(com token!)"));
            }
            return terminal.getLexema();
        }

        @Override
        public Object aoReduzir(Producao p, List<Object> valores, int linha) {
            reducoes++;
            for (Object v : valores) {
                if (v == null) {
                    filhosNulos++;
                }
            }
            return p.getCabeca();
        }
    }

    private static ResultadoAnalise analisarComRegistro(String fonte, AcoesRegistro acoes) {
        return new AnalisadorSintatico(TABELA, new TabelaPalavrasReservadas(), acoes).analisar(fonte);
    }

    private static void verificarAcoes() {
        AcoesRegistro ok = new AcoesRegistro();
        ResultadoAnalise rOk = analisarComRegistro("program p; begin x := 1; end.", ok);
        checar("programa correto: valor final da ação (PROG), nenhum terminal inserido, nenhum filho null",
                rOk.isSucesso() && "PROG".equals(rOk.getValor()) && ok.inseridos.isEmpty() && ok.filhosNulos == 0);

        // Modo pânico: "if x > 1" é descartado como se fosse um comando inteiro; o
        // não terminal recuperado entra na pilha com valor null.
        AcoesRegistro panico = new AcoesRegistro();
        ResultadoAnalise rPanico = analisarComRegistro(linhas(
                "program p;", "begin", "  if x > 1 x := 2;", "  y := 3;", "end."), panico);
        checar("modo pânico: as reduções sobre o não terminal recuperado recebem null, nada é inserido",
                panico.filhosNulos > 0 && panico.inseridos.isEmpty());
        checar("com erro sintático, o valor final é null (as reduções continuam até o fim)",
                rPanico.getValor() == null && panico.reducoes > 0 && !rPanico.isSucesso());

        checarInseridos("falta 'then' antes de um if com else: 'then' inserido",
                "program p; begin if x > 1 x := 2 else x := 3; y := 1; end.", "then@1");
        checarInseridos("falta ';' depois do nome do programa: ';' inserido na linha do 'begin'",
                linhas("program p", "begin x := 1; end."), ";@2");
        checarInseridos("procedimento sem 'begin': 'begin' inserido",
                "program p; procedure q; x := 1; begin q; end.", "begin@1");

        AcoesRegistro lexico = new AcoesRegistro();
        ResultadoAnalise rLexico = analisarComRegistro("program p; begin x := 1; @ end.", lexico);
        checar("só erro léxico: o valor final existe (o texto sem o token inválido é correto), mas não é sucesso",
                "PROG".equals(rLexico.getValor()) && !rLexico.isSucesso() && rLexico.getErros().size() == 1);
    }

    // Os terminais inseridos chegam às ações marcados (isInserido, sem token), com a
    // linha do token diante do qual foram inseridos.
    private static void checarInseridos(String descricao, String fonte, String... esperados) {
        AcoesRegistro acoes = new AcoesRegistro();
        ResultadoAnalise r = analisarComRegistro(fonte, acoes);
        List<String> lista = Arrays.asList(esperados);
        boolean ok = acoes.inseridos.equals(lista) && r.getValor() == null && !r.isSucesso();
        checar(descricao + (ok ? "" : " (obtido: " + acoes.inseridos + ")"), ok);
    }

    // ---------------------------------------------------------------- terminais

    private static void verificarTerminais() {
        Terminal inserido = Terminal.inserido("then", 7);
        checar("terminal inserido: nome, linha, lexema = nome, sem token, sem posição",
                inserido.getNome().equals("then") && inserido.getLinha() == 7 && inserido.getLexema().equals("then")
                        && inserido.getToken() == null && inserido.getPosicao() == -1);
        checar("terminal inserido: isInserido e não é fim de arquivo",
                inserido.isInserido() && !inserido.isFimDeArquivo());
        boolean recusou = false;
        try {
            Terminal.inserido("$", 1);
        } catch (IllegalArgumentException e) {
            recusou = true;
        }
        checar("o fim de arquivo não pode ser um terminal inserido", recusou);
        Terminal fim = Terminal.fimDeArquivo(3, 10);
        checar("fim de arquivo: não é inserido, lexema 'fim do arquivo', posição 10",
                fim.isFimDeArquivo() && !fim.isInserido() && fim.getLexema().equals("fim do arquivo")
                        && fim.getPosicao() == 10);
        Terminal comum = new Terminal("id", new Token("x", TipoToken.IDENTIFICADOR, 2), 4);
        checar("terminal do texto: nem inserido nem fim de arquivo, com token e posição",
                !comum.isInserido() && !comum.isFimDeArquivo() && comum.getToken() != null
                        && comum.getLexema().equals("x") && comum.getPosicao() == 4);
    }

    // --------------------------------------------------------------- expressões

    private static void verificarExpressoes() {
        AnalisadorSintatico expr = new AnalisadorSintatico(
                AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_EXPRESSOES));
        String esperadoFator = "esperado número, identificador, '(', constante string ou constante char";
        checarSaida(expr, "expressões: dois parênteses com operando faltando", "(1 + ) * (2 * )",
                "Erro sintático na linha 1: Encontrado ')'; " + esperadoFator,
                "Erro sintático na linha 1: Encontrado ')'; " + esperadoFator);
        checarSaida(expr, "expressões: operador sobrando e ')' sobrando", "1 + * 2 + 3 + 4 + 5 + 6 ) + 7",
                "Erro sintático na linha 1: Encontrado '*'; " + esperadoFator,
                "Erro sintático na linha 1: Encontrado ')'; esperado '=', '<', '>', '<>', '<=', '>=', '+', '-', '*', "
                        + "'div', 'mod' ou fim do arquivo");
        checarSaida(expr, "expressões: parênteses não fechados", "( ( 1",
                "Erro sintático na linha 1: Fim do arquivo inesperado; esperado '=', '<', '>', '<>', '<=', '>=', '+', "
                        + "'-', '*', 'div', 'mod' ou ')'");
        checarSaida(expr, "expressões: correta continua OK", "1 + 2 + 3 + 4 + 5 + 6 + 7", "OK");
    }

    // ---------------------------------------------------------------- robustez

    private static final String[] PEDACOS = {"program", "p", ";", "begin", "end", "if", "then", "else", "while",
            "do", "var", "procedure", ":=", ":", ",", ".", "(", ")", "+", "-", "*", "div", "mod", "=", "<", ">",
            "<>", "x", "1", "2.5", "integer", "'a'", "\"s\"", "@", "for", "/", "\n", "\n", "{", "\"abc", "/*"};

    private static void verificarRobustez() {
        Random rnd = new Random(2026);
        int excecoes = 0;
        int foraDeOrdem = 0;
        int linhaInvalida = 0;
        int naoDeterministico = 0;
        int programas = 3000;
        String exemplo = null;
        for (int i = 0; i < programas; i++) {
            StringBuilder sb = new StringBuilder();
            int n = rnd.nextInt(60);
            if (i % 2 == 0) {
                sb.append("program p; begin ");
            }
            for (int k = 0; k < n; k++) {
                sb.append(PEDACOS[rnd.nextInt(PEDACOS.length)]).append(' ');
            }
            if (i % 3 == 0) {
                sb.append("end.");
            }
            String fonte = sb.toString();
            int ultimaLinha = fonte.split("\n", -1).length;
            try {
                ResultadoAnalise r = ANALISADOR.analisar(fonte);
                List<Integer> todas = linhasDosErros(r, null);
                for (int k = 1; k < todas.size(); k++) {
                    if (todas.get(k) < todas.get(k - 1)) {
                        foraDeOrdem++;
                        exemplo = fonte;
                        break;
                    }
                }
                for (int linha : todas) {
                    if (linha < 1 || linha > ultimaLinha) {
                        linhaInvalida++;
                        exemplo = fonte;
                        break;
                    }
                }
                if (!r.formatar().equals(ANALISADOR.analisar(fonte).formatar())) {
                    naoDeterministico++;
                }
                if (r.getErros().isEmpty() != r.isSucesso()) {
                    excecoes++;
                }
            } catch (RuntimeException | StackOverflowError e) {
                excecoes++;
                exemplo = fonte + " -> " + e;
            }
        }
        checar(programas + " textos aleatórios: nenhuma exceção" + (excecoes == 0 ? "" : " (ex.: " + exemplo + ")"),
                excecoes == 0);
        checar("textos aleatórios: erros sempre em ordem de linha" + (foraDeOrdem == 0 ? "" : " (ex.: " + exemplo + ")"),
                foraDeOrdem == 0);
        checar("textos aleatórios: toda linha de erro existe no texto" + (linhaInvalida == 0 ? "" : " (ex.: " + exemplo + ")"),
                linhaInvalida == 0);
        checar("textos aleatórios: a mesma entrada sempre dá a mesma saída", naoDeterministico == 0);
    }

    // -------------------------------------------------------------- desempenho

    private static void verificarDesempenho() {
        Random rnd = new Random(7);
        // Sem os pedaços que abrem comentário ou string (eles engoliriam o resto do texto).
        List<String> pedacos = new ArrayList<>(Arrays.asList(PEDACOS));
        pedacos.removeAll(Arrays.asList("{", "/*", "\"abc"));
        StringBuilder lixo = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            lixo.append(pedacos.get(rnd.nextInt(pedacos.size()))).append(' ');
        }
        long inicio = System.nanoTime();
        ResultadoAnalise r = ANALISADOR.analisar(lixo.toString());
        double segundos = (System.nanoTime() - inicio) / 1e9;
        checar(String.format("20000 tokens aleatórios: termina em %.2f s (limite 10 s), com %d erros (mais de 100)",
                segundos, r.getErros().size()), segundos < 10 && r.getErros().size() > 100);

        // Pilha profunda (1000 'begin' abertos) e 300 erros: cada recuperação
        // examina a pilha inteira, então isso mede o custo dessa busca.
        StringBuilder fundo = new StringBuilder("program p; begin ");
        for (int i = 0; i < 1000; i++) {
            fundo.append("begin ");
        }
        for (int i = 0; i < 300; i++) {
            fundo.append("x := 1 + ; ");
        }
        for (int i = 0; i < 1000; i++) {
            fundo.append("end; ");
        }
        fundo.append("end.");
        inicio = System.nanoTime();
        r = ANALISADOR.analisar(fundo.toString());
        segundos = (System.nanoTime() - inicio) / 1e9;
        int sintaticos = linhasDosErros(r, CategoriaErro.SINTATICO).size();
        checar(String.format("1000 blocos abertos e 300 erros: os 300 relatados, em %.2f s (limite 10 s)", segundos),
                segundos < 10 && sintaticos == 300);
    }

    // ------------------------------------------------------------------ apoio

    private static List<Integer> linhasDosErros(ResultadoAnalise r, CategoriaErro categoria) {
        List<Integer> linhas = new ArrayList<>();
        for (ErroCompilacao e : r.getErros()) {
            if (categoria == null || e.getCategoria() == categoria) {
                linhas.add(e.getLinha());
            }
        }
        return linhas;
    }

    private static String linhas(String... linhas) {
        return String.join("\n", linhas);
    }

    private static void checarSaida(String descricao, String fonte, String... esperado) {
        checarSaida(ANALISADOR, descricao, fonte, esperado);
    }

    private static void checarSaida(AnalisadorSintatico analisador, String descricao, String fonte, String... esperado) {
        String obtido = analisador.analisar(fonte).formatar();
        String esperadoTexto = String.join("\n", esperado);
        boolean ok = obtido.equals(esperadoTexto);
        checar(descricao + (ok ? "" : " (obtido: " + obtido + ")"), ok);
    }

    private static void executar(String parte, Runnable verificacoes) {
        try {
            verificacoes.run();
        } catch (RuntimeException e) {
            checar(parte + ": exceção inesperada " + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
    }

    private static void terminar() {
        System.out.println();
        System.out.println(total + " verificações da recuperação de erros, " + falhas + " falha(s).");
        System.exit(falhas > 0 ? 1 : 0);
    }

    private static void checar(String descricao, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("[OK]    " + descricao);
        } else {
            falhas++;
            System.out.println("[FALHA] " + descricao);
        }
    }
}
