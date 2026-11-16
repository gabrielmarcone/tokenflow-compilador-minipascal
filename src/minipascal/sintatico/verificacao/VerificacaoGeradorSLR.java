package minipascal.sintatico.verificacao;

import minipascal.cli.Console;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.GramaticaInvalidaException;
import minipascal.sintatico.gramatica.LeitorGramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.slr.Acao;
import minipascal.sintatico.slr.AnaliseSLR;
import minipascal.sintatico.slr.ConflitoSLR;
import minipascal.sintatico.slr.ConflitoSLRException;
import minipascal.sintatico.slr.ConjuntosFirstFollow;
import minipascal.sintatico.slr.ExportadorTabela;
import minipascal.cli.GeradorTabelas;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifica o gerador de tabelas SLR(1): leitura da gramática, FIRST/FOLLOW,
 * coleção LR(0), tabela ACTION/GOTO, detecção de conflitos e exportação.
 *
 * As referências externas são dois exemplos do livro do Dragão (Aho, Lam,
 * Sethi e Ullman, Compiladores: princípios, técnicas e ferramentas, 2ª ed.):
 * a gramática de expressões 4.1, cuja tabela SLR completa está na figura 4.37,
 * e a gramática 4.28, com FIRST e FOLLOW calculados no exemplo 4.30.
 */
public class VerificacaoGeradorSLR {

    private static int total = 0;
    private static int falhas = 0;

    // Gramática 4.1 do livro, sem alterações.
    private static final String DRAGAO =
            "E -> E + T | T\n" +
            "T -> T * F | F\n" +
            "F -> ( E ) | id\n";

    // Gramática 4.28 do livro (E' e T' viram E2 e T2: o apóstrofo é reservado ao S' aumentado).
    private static final String DRAGAO_LL =
            "E -> T E2\n" +
            "E2 -> + T E2 | ε\n" +
            "T -> F T2\n" +
            "T2 -> * F T2 | eps\n" +
            "F -> ( E ) | id\n";

    public static void main(String[] args) {
        // Cada parte roda protegida: uma exceção vira falha e as demais continuam.
        executar("leitor", VerificacaoGeradorSLR::verificarLeitor);
        executar("erros do leitor", VerificacaoGeradorSLR::verificarErrosDoLeitor);
        executar("FIRST/FOLLOW", VerificacaoGeradorSLR::verificarFirstFollow);
        executar("tabela do livro", VerificacaoGeradorSLR::verificarTabelaDoLivro);
        executar("conflitos", VerificacaoGeradorSLR::verificarConflitos);
        executar("gramática das expressões", VerificacaoGeradorSLR::verificarGramaticaDasExpressoes);
        executar("exportador", VerificacaoGeradorSLR::verificarExportador);
        executar("linha de comando", VerificacaoGeradorSLR::verificarLinhaDeComando);

        System.out.println();
        System.out.println(total + " verificacoes do gerador SLR, " + falhas + " falha(s).");
        if (falhas > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ leitor

    private static void verificarLeitor() {
        Gramatica g = LeitorGramatica.lerTexto(DRAGAO);
        checar("gramática do livro: 6 produções + a aumentada", g.getProducoes().size() == 7);
        checar("produção 0 é E' -> E", g.getProducao(0).toString().equals("E' -> E"));
        checar("produções numeradas na ordem do arquivo",
                g.getProducao(1).toString().equals("E -> E + T")
                        && g.getProducao(2).toString().equals("E -> T")
                        && g.getProducao(6).toString().equals("F -> id"));
        checar("terminais na ordem de aparição, com $ no fim",
                new ArrayList<>(g.getTerminais()).equals(Arrays.asList("+", "*", "(", ")", "id", "$")));
        checar("não terminais na ordem das cabeças",
                new ArrayList<>(g.getNaoTerminais()).equals(Arrays.asList("E", "T", "F")));
        checar("símbolo inicial é a cabeça da primeira regra", g.getSimboloInicial().equals("E"));

        Gramatica comContinuacao = LeitorGramatica.lerTexto(
                "# comentário\n\nS -> a\n   | b | c\r\n  | ( S )\n");
        checar("linha começando com | continua a regra anterior (4 produções de S)",
                comContinuacao.producoesDe("S").size() == 4
                        && comContinuacao.getProducao(4).toString().equals("S -> ( S )"));

        Gramatica vazia = LeitorGramatica.lerTexto(DRAGAO_LL);
        checar("ε e eps viram produção vazia",
                vazia.getProducao(3).isVazia() && vazia.getProducao(6).isVazia()
                        && vazia.getProducao(3).toString().equals("E2 -> ε"));

        Gramatica expressoes = LeitorGramatica.lerRecurso("expressoes.txt");
        checar("gramática embutida expressoes.txt: 22 produções", expressoes.getProducoes().size() == 23);
        checar("expressoes.txt: 5 não terminais, EXPR é o inicial",
                expressoes.getNaoTerminais().size() == 5 && expressoes.getSimboloInicial().equals("EXPR"));
        checar("expressoes.txt: 17 terminais + $",
                expressoes.getTerminais().size() == 18 && expressoes.getTerminais().contains("constante_char")
                        && expressoes.getTerminais().contains("div") && !expressoes.getTerminais().contains("/"));
    }

    private static void verificarErrosDoLeitor() {
        checarErro("arquivo sem produções", "# só comentário\n", "nenhuma produção");
        checarErro("linha sem '->'", "S -> a\nT a b\n", "linha 2");
        checarErro("cabeça em minúsculas", "s -> a\n", "linha 1");
        checarErro("alternativa vazia entre barras", "S -> a | | b\n", "alternativa vazia");
        checarErro("ε junto com outros símbolos", "S -> a\nA -> ε b\n", "sozinho");
        checarErro("| sem regra anterior", "| a\n", "linha 1");
        checarErro("não terminal sem produção, com a linha do uso", "S -> a B\nC -> c\n", "linha 1");
        checarErro("não terminal inalcançável", "S -> a\nX -> b\n", "não é alcançável");
        checarErro("não terminal que não deriva terminais", "S -> a | B\nB -> B b\n", "não deriva");
        checarErro("$ usado na gramática", "S -> a $\n", "reservado");
        checarErro("produção repetida", "S -> b | a\nS -> b\n", "a produção 3 (S -> b) repete a produção 1");
        checarErro("ε repetido", "S -> a A\nA -> ε | eps\n", "repete");
        checarErro("recurso inexistente", null, "não encontrado");
    }

    // ------------------------------------------------------------- FIRST/FOLLOW

    private static void verificarFirstFollow() {
        ConjuntosFirstFollow c = new ConjuntosFirstFollow(LeitorGramatica.lerTexto(DRAGAO));
        checar("FIRST(E) = FIRST(T) = FIRST(F) = { ( id }",
                c.first("E").equals(conjunto("(", "id")) && c.first("T").equals(conjunto("(", "id"))
                        && c.first("F").equals(conjunto("(", "id")));
        checar("FOLLOW(E) = { + ) $ }", c.follow("E").equals(conjunto("+", ")", "$")));
        checar("FOLLOW(T) = FOLLOW(F) = { + * ) $ }",
                c.follow("T").equals(conjunto("+", "*", ")", "$"))
                        && c.follow("F").equals(conjunto("+", "*", ")", "$")));
        checar("gramática sem ε não tem anuláveis", c.getAnulaveis().isEmpty());

        // Exemplo 4.30 do livro
        ConjuntosFirstFollow ll = new ConjuntosFirstFollow(LeitorGramatica.lerTexto(DRAGAO_LL));
        checar("anuláveis = { E2 T2 }", ll.getAnulaveis().equals(conjunto("E2", "T2")));
        checar("FIRST(E) = FIRST(T) = FIRST(F) = { ( id }",
                ll.first("E").equals(conjunto("(", "id")) && ll.first("T").equals(conjunto("(", "id"))
                        && ll.first("F").equals(conjunto("(", "id")));
        checar("FIRST(E2) = { + } e FIRST(T2) = { * } (ε fica nos anuláveis)",
                ll.first("E2").equals(conjunto("+")) && ll.first("T2").equals(conjunto("*")));
        checar("FOLLOW(E) = FOLLOW(E2) = { ) $ }",
                ll.follow("E").equals(conjunto(")", "$")) && ll.follow("E2").equals(conjunto(")", "$")));
        checar("FOLLOW(T) = FOLLOW(T2) = { + ) $ }",
                ll.follow("T").equals(conjunto("+", ")", "$")) && ll.follow("T2").equals(conjunto("+", ")", "$")));
        checar("FOLLOW(F) = { + * ) $ }", ll.follow("F").equals(conjunto("+", "*", ")", "$")));
        checar("FIRST de sequência atravessa anuláveis: FIRST(T2 E2 F) = { * + ( id }",
                ll.firstDaSequencia(Arrays.asList("T2", "E2", "F")).equals(conjunto("*", "+", "(", "id")));
    }

    // --------------------------------------------------- tabela da figura 4.37

    private static void verificarTabelaDoLivro() {
        TabelaSLR t = TabelaSLR.construir(LeitorGramatica.lerTexto(DRAGAO));
        checar("12 estados (I0 a I11)", t.numeroDeEstados() == 12);

        // Figura 4.37, linha por linha. Colunas: id + * ( ) $ | E T F
        String[][] figura = {
                {"s5", "", "", "s4", "", "", "1", "2", "3"},
                {"", "s6", "", "", "", "acc", "", "", ""},
                {"", "r2", "s7", "", "r2", "r2", "", "", ""},
                {"", "r4", "r4", "", "r4", "r4", "", "", ""},
                {"s5", "", "", "s4", "", "", "8", "2", "3"},
                {"", "r6", "r6", "", "r6", "r6", "", "", ""},
                {"s5", "", "", "s4", "", "", "", "9", "3"},
                {"s5", "", "", "s4", "", "", "", "", "10"},
                {"", "s6", "", "", "s11", "", "", "", ""},
                {"", "r1", "s7", "", "r1", "r1", "", "", ""},
                {"", "r3", "r3", "", "r3", "r3", "", "", ""},
                {"", "r5", "r5", "", "r5", "r5", "", "", ""},
        };
        String[] terminais = {"id", "+", "*", "(", ")", "$"};
        String[] naoTerminais = {"E", "T", "F"};
        List<String> diferencas = new ArrayList<>();
        for (int estado = 0; estado < figura.length; estado++) {
            for (int c = 0; c < terminais.length; c++) {
                Acao a = t.acao(estado, terminais[c]);
                String obtido = a == null ? "" : a.toString();
                if (!obtido.equals(figura[estado][c])) {
                    diferencas.add("ACTION[" + estado + "," + terminais[c] + "]=" + obtido);
                }
            }
            for (int c = 0; c < naoTerminais.length; c++) {
                int d = t.desvio(estado, naoTerminais[c]);
                String obtido = d < 0 ? "" : String.valueOf(d);
                if (!obtido.equals(figura[estado][terminais.length + c])) {
                    diferencas.add("GOTO[" + estado + "," + naoTerminais[c] + "]=" + obtido);
                }
            }
        }
        checar("tabela ACTION/GOTO idêntica à figura 4.37 do livro, célula por célula"
                + (diferencas.isEmpty() ? "" : " " + diferencas), diferencas.isEmpty());

        checar("terminais esperados no estado 0: id e (",
                t.terminaisEsperados(0).equals(Arrays.asList("(", "id")));
        checar("terminal inexistente na célula devolve null; GOTO inexistente devolve -1",
                t.acao(0, "+") == null && t.acao(0, "xyz") == null && t.desvio(1, "E") == -1);

        checar("aceita id + id * id", aceita(t, "id + id * id"));
        checar("aceita ( id + id ) * id", aceita(t, "( id + id ) * id"));
        checar("recusa id + * id", !aceita(t, "id + * id"));
        checar("recusa ( id", !aceita(t, "( id"));
        checar("recusa entrada vazia", !aceita(t, ""));

        checar("duas construções da mesma gramática dão o mesmo relatório (numeração determinística)",
                ExportadorTabela.relatorio(new AnaliseSLR(LeitorGramatica.lerTexto(DRAGAO)), "x")
                        .equals(ExportadorTabela.relatorio(t.getAnalise(), "x")));
    }

    // ---------------------------------------------------------------- conflitos

    private static void verificarConflitos() {
        ConflitoSLRException e1 = conflitoDe("E -> E + E | id\n");
        checar("gramática ambígua E -> E + E: conflito empilhar/reduzir em '+'",
                e1 != null && e1.getConflitos().size() == 1
                        && e1.getConflitos().get(0).getTerminal().equals("+")
                        && e1.getConflitos().get(0).getTipo().equals("empilhar/reduzir"));

        ConflitoSLRException e2 = conflitoDe("S -> A | B\nA -> x\nB -> x\n");
        checar("A -> x e B -> x: conflito reduzir/reduzir em '$' entre r3 e r4",
                e2 != null && e2.getConflitos().size() == 1
                        && e2.getConflitos().get(0).getTerminal().equals("$")
                        && e2.getConflitos().get(0).getTipo().equals("reduzir/reduzir")
                        && e2.getConflitos().get(0).getAcoes().equals(Arrays.asList(Acao.reduzir(3), Acao.reduzir(4))));

        // O else pendente, como na gramática original do enunciado.
        ConflitoSLRException e3 = conflitoDe(
                "C -> if e then C | if e then C else C | a\n");
        checar("else pendente: um único conflito, empilhar/reduzir em 'else'",
                e3 != null && e3.getConflitos().size() == 1
                        && e3.getConflitos().get(0).getTerminal().equals("else")
                        && e3.getConflitos().get(0).getTipo().equals("empilhar/reduzir"));
        checar("mensagem da exceção cita estado, terminal e as ações",
                e3 != null && e3.getMessage().contains("1 conflito(s)")
                        && e3.getMessage().contains("terminal 'else'")
                        && e3.getMessage().contains(e3.getConflitos().get(0).getAcoes().get(0).toString()));

        // A versão com comandos abertos/fechados (opção B do grupo) não tem conflito.
        boolean semConflito;
        try {
            TabelaSLR t = TabelaSLR.construir(LeitorGramatica.lerTexto(
                    "C -> F | A\n" +
                    "F -> if e then F else F | a\n" +
                    "A -> if e then C | if e then F else A\n"));
            semConflito = aceita(t, "if e then if e then a else a")
                    && aceita(t, "if e then a else if e then a")
                    && !aceita(t, "if e then a else");
        } catch (ConflitoSLRException e) {
            semConflito = false;
        }
        checar("comandos abertos/fechados eliminam o conflito do else", semConflito);

        AnaliseSLR comConflito = new AnaliseSLR(LeitorGramatica.lerTexto("E -> E + E | id\n"));
        String rel = ExportadorTabela.relatorio(comConflito, "ambigua");
        checar("conflito da gramática ambígua: estado 4, '+', entre s3 e r1 (conferido à mão)",
                comConflito.getConflitos().size() == 1
                        && comConflito.getConflitos().get(0).toString()
                        .equals("estado 4, terminal '+': conflito empilhar/reduzir entre s3 e r1"));
        checar("relatório mostra a célula em conflito como s3/r1 e lista o conflito",
                rel.contains("4      | s3/r1 |") && rel.contains("== Conflitos ==\nestado 4, terminal '+'"));
        checar("gravar() de gramática com conflito grava só o relatório, sem o CSV",
                gravaSoRelatorio(comConflito));
    }

    // ------------------------------------------------- gramática das expressões

    private static void verificarGramaticaDasExpressoes() {
        TabelaSLR t;
        try {
            t = TabelaSLR.construir(LeitorGramatica.lerRecurso("expressoes.txt"));
        } catch (ConflitoSLRException e) {
            checar("expressoes.txt sem conflitos: " + e.getMessage(), false);
            return;
        }
        checar("expressoes.txt é SLR(1), sem conflitos", true);
        checar("expressoes.txt: 34 estados LR(0)", t.numeroDeEstados() == 34);

        ConjuntosFirstFollow c = t.getAnalise().getConjuntos();
        checar("FOLLOW(EXPR) = { ) $ }", c.follow("EXPR").equals(conjunto(")", "$")));
        checar("FOLLOW(SIEXPR) = operadores relacionais, + - ) $",
                c.follow("SIEXPR").equals(conjunto("=", "<", ">", "<>", "<=", ">=", "+", "-", ")", "$")));
        checar("FOLLOW(FATOR) = FOLLOW(TERMO) = * div mod + relacionais + - ) $",
                c.follow("FATOR").equals(c.follow("TERMO"))
                        && c.follow("FATOR").equals(conjunto("=", "<", ">", "<>", "<=", ">=", "+", "-", "*",
                        "div", "mod", ")", "$")));
        checar("terminais esperados no início: + - numero id ( constante_string constante_char",
                t.terminaisEsperados(0).equals(Arrays.asList("+", "-", "numero", "id", "(",
                        "constante_string", "constante_char")));

        String[] validas = {
                "id", "numero", "constante_string", "constante_char",
                "id + numero * ( id - numero )", "- id", "+ numero", "id <= numero",
                "( ( id ) )", "id mod numero div id", "- id * id + numero = id",
                "id <> constante_char", "( id < numero )"};
        for (String v : validas) {
            checar("expressão válida: " + v, aceita(t, v));
        }
        String[] invalidas = {
                "", "id <", "id < id < id", "+ - id", "id * - id", "( id", "id )",
                "id id", "id = = id", "id / id", "id and id", "not id", "( )", "mod id"};
        for (String v : invalidas) {
            checar("expressão inválida: " + (v.isEmpty() ? "(vazia)" : v), !aceita(t, v));
        }

        // A precedência vem da gramática: em "id + id * id" a primeira redução por
        // SIEXPR -> SIEXPR + TERMO só acontece depois de TERMO -> TERMO * FATOR.
        List<Integer> reducoes = reducoes(t, "id + id * id");
        int multiplicacao = indiceDaProducao(t.getGramatica(), "TERMO -> TERMO * FATOR");
        int soma = indiceDaProducao(t.getGramatica(), "SIEXPR -> SIEXPR + TERMO");
        checar("precedência: * é reduzido antes de + em id + id * id",
                reducoes.indexOf(multiplicacao) >= 0 && reducoes.indexOf(multiplicacao) < reducoes.indexOf(soma));
        List<Integer> esquerda = reducoes(t, "id - id - id");
        int subtracao = indiceDaProducao(t.getGramatica(), "SIEXPR -> SIEXPR - TERMO");
        checar("associatividade à esquerda: id - id - id reduz SIEXPR - TERMO duas vezes",
                esquerda.indexOf(subtracao) >= 0 && esquerda.lastIndexOf(subtracao) > esquerda.indexOf(subtracao));
    }

    // --------------------------------------------------------------- exportador

    private static void verificarExportador() {
        AnaliseSLR a = TabelaSLR.construir(LeitorGramatica.lerRecurso("expressoes.txt")).getAnalise();
        String rel = ExportadorTabela.relatorio(a, "expressoes.txt");
        checar("relatório tem todas as seções",
                rel.contains("== Gramática (22 produções") && rel.contains("== FIRST ==")
                        && rel.contains("== FOLLOW ==") && rel.contains("== Estados LR(0) (34) ==")
                        && rel.contains("== Tabela ACTION / GOTO") && rel.contains("nenhum: a gramática é SLR(1)."));
        checar("relatório mostra itens com o ponto e as transições",
                rel.contains("EXPR' -> • EXPR") && rel.contains("transições: "));

        String csv = ExportadorTabela.csv(a);
        String[] linhas = csv.split("\n");
        checar("CSV: cabeçalho + uma linha por estado", linhas.length == 1 + 34);
        checar("CSV: cabeçalho começa com estado e termina nos não terminais",
                linhas[0].startsWith("estado;=;<;>;<>;<=;>=;+;-;*;div;mod;numero;id;(;);constante_string;constante_char;$;") && linhas[0].endsWith(";EXPR;OPREL;SIEXPR;TERMO;FATOR"));
        checar("CSV: cada linha tem o mesmo número de campos (1 + 18 + 5)",
                todasComCampos(linhas, 24));

        AnaliseSLR comPontoEVirgula = new AnaliseSLR(LeitorGramatica.lerTexto("L -> L ; a | a\n"));
        String csv2 = ExportadorTabela.csv(comPontoEVirgula);
        checar("CSV: terminal ';' vai entre aspas no cabeçalho",
                csv2.split("\n")[0].equals("estado;\";\";a;$;L"));
    }

    // -------------------------------------------------------- linha de comando

    private static void verificarLinhaDeComando() {
        try {
            java.nio.file.Path pasta = java.nio.file.Files.createTempDirectory("tokenflow_gerador");
            java.nio.file.Path ambigua = pasta.resolve("ambigua.txt");
            java.nio.file.Files.write(ambigua, "E -> E + E | id\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            int semArgumentos = executarCalado(new String[0]);
            int inexistente = executarCalado(new String[]{pasta.resolve("nao_existe.txt").toString()});
            int embutida = executarCalado(new String[]{"expressoes.txt", pasta.toString()});
            int caminhoComPasta = executarCalado(new String[]{pasta.resolve("sub").resolve("expressoes.txt").toString()});
            int comConflito = executarCalado(new String[]{ambigua.toString(), pasta.toString()});

            checar("linha de comando: sem argumentos devolve 1", semArgumentos == 1);
            checar("linha de comando: arquivo inexistente devolve 1", inexistente == 1);
            checar("linha de comando: gramática embutida devolve 0 e grava .txt e .csv",
                    embutida == 0 && java.nio.file.Files.exists(pasta.resolve("expressoes_tabela_slr.txt"))
                            && java.nio.file.Files.exists(pasta.resolve("expressoes_tabela_slr.csv")));
            checar("linha de comando: caminho com pasta inexistente não usa a gramática embutida (devolve 1)",
                    caminhoComPasta == 1);
            checar("linha de comando: gramática com conflito devolve 1 e grava só o relatório",
                    comConflito == 1 && java.nio.file.Files.exists(pasta.resolve("ambigua_tabela_slr.txt"))
                            && !java.nio.file.Files.exists(pasta.resolve("ambigua_tabela_slr.csv")));
            checar("linha de comando: o .txt gravado é igual ao relatório em memória",
                    new String(java.nio.file.Files.readAllBytes(pasta.resolve("expressoes_tabela_slr.txt")),
                            java.nio.charset.StandardCharsets.UTF_8).equals(ExportadorTabela.relatorio(
                            new AnaliseSLR(LeitorGramatica.lerRecurso("expressoes.txt")), "expressoes.txt")));

            try (java.util.stream.Stream<java.nio.file.Path> arquivos = java.nio.file.Files.list(pasta)) {
                for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) arquivos::iterator) {
                    java.nio.file.Files.deleteIfExists(p);
                }
            }
            java.nio.file.Files.deleteIfExists(pasta);
        } catch (java.io.IOException e) {
            checar("linha de comando: erro de E/S no teste: " + e.getMessage(), false);
        }
    }

    // Roda o gerador sem deixar as mensagens dele no meio do resultado da suíte.
    private static int executarCalado(String[] args) {
        java.io.PrintStream original = System.out;
        try {
            System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
            return GeradorTabelas.executar(args);
        } finally {
            System.setOut(original);
        }
    }

    // ------------------------------------------------------------------ apoio

    private static void executar(String parte, Runnable verificacoes) {
        try {
            verificacoes.run();
        } catch (RuntimeException e) {
            checar(parte + ": exceção inesperada " + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
    }

    // Simula o analisador SLR sobre uma sequência de terminais (separados por espaço).
    private static boolean aceita(TabelaSLR t, String entrada) {
        return reducoesOuNull(t, entrada) != null;
    }

    private static List<Integer> reducoes(TabelaSLR t, String entrada) {
        List<Integer> r = reducoesOuNull(t, entrada);
        return r == null ? new ArrayList<Integer>() : r;
    }

    private static List<Integer> reducoesOuNull(TabelaSLR t, String entrada) {
        List<String> tokens = new ArrayList<>();
        for (String s : entrada.trim().split("\\s+")) {
            if (!s.isEmpty()) {
                tokens.add(s);
            }
        }
        tokens.add(Gramatica.FIM_DE_ENTRADA);
        List<Integer> pilha = new ArrayList<>();
        pilha.add(0);
        List<Integer> reducoes = new ArrayList<>();
        int i = 0;
        for (int passos = 0; passos < 10000; passos++) {
            Acao a = t.acao(pilha.get(pilha.size() - 1), tokens.get(i));
            if (a == null) {
                return null;
            }
            if (a.isAceitar()) {
                return reducoes;
            }
            if (a.isEmpilhar()) {
                pilha.add(a.getAlvo());
                i++;
            } else {
                Producao p = t.getGramatica().getProducao(a.getAlvo());
                for (int k = 0; k < p.tamanho(); k++) {
                    pilha.remove(pilha.size() - 1);
                }
                int destino = t.desvio(pilha.get(pilha.size() - 1), p.getCabeca());
                if (destino < 0) {
                    return null;
                }
                pilha.add(destino);
                reducoes.add(p.getNumero());
            }
        }
        return null;
    }

    private static int indiceDaProducao(Gramatica g, String texto) {
        for (Producao p : g.getProducoes()) {
            if (p.toString().equals(texto)) {
                return p.getNumero();
            }
        }
        return -2;
    }

    private static ConflitoSLRException conflitoDe(String gramatica) {
        try {
            TabelaSLR.construir(LeitorGramatica.lerTexto(gramatica));
            return null;
        } catch (ConflitoSLRException e) {
            return e;
        }
    }

    private static boolean gravaSoRelatorio(AnaliseSLR analise) {
        try {
            java.nio.file.Path pasta = java.nio.file.Files.createTempDirectory("tokenflow_slr");
            List<java.nio.file.Path> gravados = ExportadorTabela.gravar(analise, "t", pasta, "t");
            boolean ok = gravados.size() == 1
                    && gravados.get(0).getFileName().toString().equals("t_tabela_slr.txt")
                    && java.nio.file.Files.exists(gravados.get(0))
                    && !java.nio.file.Files.exists(pasta.resolve("t_tabela_slr.csv"));
            for (java.nio.file.Path p : gravados) {
                java.nio.file.Files.deleteIfExists(p);
            }
            java.nio.file.Files.deleteIfExists(pasta);
            return ok;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static boolean todasComCampos(String[] linhas, int campos) {
        for (String l : linhas) {
            // nenhuma célula da tabela das expressões contém ";", então split simples basta
            if (l.split(";", -1).length != campos) {
                return false;
            }
        }
        return true;
    }

    private static void checarErro(String descricao, String gramatica, String trecho) {
        try {
            if (gramatica == null) {
                LeitorGramatica.lerRecurso("nao_existe.txt");
            } else {
                LeitorGramatica.lerTexto(gramatica);
            }
            checar("recusa " + descricao + " (nenhum erro lançado)", false);
        } catch (GramaticaInvalidaException e) {
            boolean ok = e.getMessage().contains(trecho);
            checar("recusa " + descricao + (ok ? "" : " (mensagem: " + e.getMessage() + ")"), ok);
        }
    }

    private static Set<String> conjunto(String... simbolos) {
        return new HashSet<>(Arrays.asList(simbolos));
    }

    private static void checar(String descricao, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("[OK]    " + Console.ascii(descricao));
        } else {
            falhas++;
            System.out.println("[FALHA] " + Console.ascii(descricao));
        }
    }
}
