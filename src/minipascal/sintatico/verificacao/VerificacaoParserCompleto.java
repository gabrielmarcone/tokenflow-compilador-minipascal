package minipascal.sintatico.verificacao;

import minipascal.cli.Console;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.cli.AnaliseSintatica;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.LeitorGramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.parser.AcoesReducao;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.parser.Terminal;
import minipascal.sintatico.slr.TabelaSLR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Verifica o analisador sintático com a gramática completa (gramatica.txt):
 * a própria gramática, programas corretos, a associação do else, erros com
 * mensagem exata (tipo, linha, encontrado e esperado), programas da Unidade I,
 * programas gerados a partir da gramática, programas grandes e a linha de
 * comando.
 *
 * As mensagens esperadas foram conferidas contra um reconhecedor de Earley
 * independente: o primeiro erro sai no primeiro token em que o programa deixa
 * de ser começo de um programa válido, e os "esperados" são exatamente os
 * terminais que manteriam o programa válido naquele ponto. Os erros seguintes
 * vêm da recuperação de erros (verificada em detalhe em VerificacaoRecuperacao);
 * aqui, cada um dos que aparecem foi conferido à mão no programa.
 */
public class VerificacaoParserCompleto {

    private static int total = 0;
    private static int falhas = 0;

    private static TabelaSLR TABELA;
    private static AnalisadorSintatico ANALISADOR;

    public static void main(String[] args) {
        // Se a gramática tiver conflito, a tabela não é construída: isso vira uma
        // falha relatada (e não uma exceção solta) e as demais partes não rodam.
        try {
            TABELA = AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA);
            ANALISADOR = new AnalisadorSintatico(TABELA);
        } catch (RuntimeException e) {
            checar("gramatica.txt gera a tabela SLR(1): " + e.getMessage(), false);
            System.out.println();
            System.out.println(total + " verificacoes do parser completo, " + falhas + " falha(s).");
            System.exit(1);
        }
        executar("gramática", VerificacaoParserCompleto::verificarGramatica);
        executar("programas válidos", VerificacaoParserCompleto::verificarValidos);
        executar("associação do else", VerificacaoParserCompleto::verificarElse);
        executar("erros", VerificacaoParserCompleto::verificarErros);
        executar("programas da Unidade I", VerificacaoParserCompleto::verificarProgramasDaUnidadeI);
        executar("programas gerados", VerificacaoParserCompleto::verificarGerados);
        executar("programas grandes", VerificacaoParserCompleto::verificarGrandes);
        executar("linha de comando", VerificacaoParserCompleto::verificarLinhaDeComando);

        System.out.println();
        System.out.println(total + " verificacoes do parser completo, " + falhas + " falha(s).");
        if (falhas > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- gramática

    private static boolean temProducao(Gramatica g, String texto) {
        for (Producao p : g.getProducoes()) {
            if (p.toString().equals(texto)) {
                return true;
            }
        }
        return false;
    }

    private static void verificarGramatica() {
        Gramatica g = TABELA.getGramatica();
        checar("gramatica.txt: 57 produções (mais a aumentada)", g.getProducoes().size() == 58);
        checar("gramatica.txt: 22 não terminais, símbolo inicial PROG",
                g.getNaoTerminais().size() == 22 && g.getSimboloInicial().equals("PROG"));
        checar("gramatica.txt: 33 terminais mais $", g.getTerminais().size() == 34);
        checar("gramatica.txt: 112 estados LR(0) e nenhum conflito (a tabela foi construída)",
                TABELA.numeroDeEstados() == 112 && !TABELA.getAnalise().temConflitos());
        // O cabeçalho do procedimento é um não terminal próprio: a análise semântica
        // abre o escopo do procedimento quando ele é reduzido, antes do bloco.
        checar("gramatica.txt: CAB_PROC -> procedure id PALIST e BLOCO_PROC -> BLOCO_PROC CAB_PROC ; BLOCO ;",
                temProducao(g, "CAB_PROC -> procedure id PALIST")
                        && temProducao(g, "BLOCO_PROC -> BLOCO_PROC CAB_PROC ; BLOCO ;"));
        checar("tabela da gramática completa é montada uma única vez",
                AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA) == TABELA);

        // Os terminais da gramática têm de ser exatamente os nomes que o
        // AdaptadorLexico produz; um nome digitado errado seria um terminal
        // que nunca aparece na entrada.
        Set<String> esperados = new HashSet<>();
        String[] nomes = {"program", "id", ";", ".", "begin", "end", "var", ":", "tipo", ",", "procedure",
                "(", ")", "if", "then", "else", "while", "do", ":=", "=", "<", ">", "<>", "<=", ">=",
                "+", "-", "*", "div", "mod", "numero", "constante_string", "constante_char", "$"};
        for (String n : nomes) {
            esperados.add(n);
        }
        checar("terminais da gramática = nomes produzidos pelo adaptador", esperados.equals(g.getTerminais()));

        // A parte das expressões é a mesma nas duas gramáticas embutidas.
        Gramatica expressoes = LeitorGramatica.lerRecurso(AnalisadorSintatico.GRAMATICA_EXPRESSOES);
        Set<String> completa = new HashSet<>();
        for (Producao p : g.getProducoes()) {
            completa.add(p.toString());
        }
        boolean contidas = true;
        for (Producao p : expressoes.getProducoes()) {
            if (p.getNumero() > 0 && !completa.contains(p.toString())) {
                contidas = false;
            }
        }
        checar("as 22 produções de expressoes.txt estão em gramatica.txt", contidas);
    }

    // ------------------------------------------------------------- válidos

    private static void verificarValidos() {
        checarOk("programa com todas as construções", linhas(
                "program exemplo;",
                "var a, b: integer;",
                "    media: real;",
                "var letra: char; nome: string;",
                "procedure soma(var r: integer; x, y: integer);",
                "var t: integer;",
                "begin",
                "  t := x + y;",
                "  r := t;",
                "end;",
                "procedure vazio;",
                "begin",
                "  writeln;",
                "end;",
                "begin",
                "  read(a, b);",
                "  soma(a, a, b);",
                "  if a > b then",
                "    if a > 10 then writeln(\"grande\") else writeln(\"medio\")",
                "  else",
                "    begin",
                "      while b > 0 do b := b - 1;",
                "      letra := 'x';",
                "    end;",
                "  vazio;",
                "  media := (a + b) div 2 * 1.5;",
                "end."));
        checarOk("programa mínimo", linhas(
                "program p; begin x := 1; end."));
        checarOk("procedimento dentro de procedimento", linhas(
                "program aninhado;",
                "procedure externo(n: integer);",
                "  procedure interno;",
                "  begin",
                "    n := n - 1;",
                "  end;",
                "begin",
                "  while n > 0 do interno;",
                "end;",
                "begin",
                "  externo(3);",
                "end."));
        checarOk("maiúsculas e comentário", linhas(
                "PROGRAM P; /* comentario */ VAR X: INTEGER; BEGIN X := -1 MOD 3; WriteLn(X); END."));
        checarOk("if/else e while aninhados", linhas(
                "program controle;",
                "var i, s: integer;",
                "begin",
                "  i := 0; s := 0;",
                "  while i < 10 do",
                "    begin",
                "      if i mod 2 = 0 then s := s + i;",
                "      if i = 3 then s := s - 1 else if i = 4 then s := s + 2 else s := s;",
                "      i := i + 1;",
                "    end;",
                "  if s >= 20 then while s > 20 do s := s - 1;",
                "  begin begin s := (s); end; end;",
                "end."));
        checarOk("constantes string e char, read e write", linhas(
                "program p; begin x := \"a\" + 'b'; read(x); write(\"fim\"); end."));
    }

    // ------------------------------------------------------- associação do else

    private static void verificarElse() {
        // As ações montam o programa com colchetes em volta de cada comando if.
        checarEstrutura("program p; begin if a then if b then x := 1 else x := 2; end.",
                "program p ; begin [if a then [if b then x := 1 else x := 2]] ; end .");
        checarEstrutura("program p; begin if a then x := 1 else if b then x := 2 else x := 3; end.",
                "program p ; begin [if a then x := 1 else [if b then x := 2 else x := 3]] ; end .");
        checarEstrutura("program p; begin if a then begin if b then x := 1; end else x := 2; end.",
                "program p ; begin [if a then begin [if b then x := 1] ; end else x := 2] ; end .");
        checarEstrutura("program p; begin while a do if b then x := 1 else x := 2; end.",
                "program p ; begin while a do [if b then x := 1 else x := 2] ; end .");
        checarEstrutura("program p; begin if a then while b do if c then x := 1 else x := 2; end.",
                "program p ; begin [if a then while b do [if c then x := 1 else x := 2]] ; end .");
    }

    // ---------------------------------------------------------------- erros

    private static void verificarErros() {
        checarSaida("falta ';' antes de 'end'", "program p; begin x := 1 end.",
                "Erro sintático na linha 1: Encontrado 'end'; esperado ';', '=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div' ou 'mod'");
        checarSaida("';' antes de 'else'", "program p; begin if a then x := 1; else x := 2; end.",
                "Erro sintático na linha 1: Encontrado 'else'; esperado identificador, 'begin', 'end', 'if' ou 'while'");
        checarSaida("falta o '.' final", "program p; begin x := 1; end",
                "Erro sintático na linha 1: Fim do arquivo inesperado; esperado '.'");
        checarSaida("falta 'program'", "begin x := 1; end.",
                "Erro sintático na linha 1: Encontrado 'begin'; esperado 'program'");
        checarSaida("bloco sem comandos", "program p; begin end.",
                "Erro sintático na linha 1: Encontrado 'end'; esperado identificador, 'begin', 'if' ou 'while'");
        checarSaida("falta ':' na declaração", "program p; var x integer; begin x := 1; end.",
                "Erro sintático na linha 1: Encontrado tipo 'integer'; esperado ':' ou ','");
        checarSaida("'var' depois de 'procedure'", "program p; procedure q; begin x := 1; end; var x: integer; begin x := 1; end.",
                "Erro sintático na linha 1: Encontrado 'var'; esperado 'begin' ou 'procedure'");
        checarSaida("'for' não existe na gramática", "program p; begin for i := 1 to 10 do x := i; end.",
                "Erro sintático na linha 1: Encontrado 'for'; esperado identificador, 'begin', 'if' ou 'while'");
        checarSaida("'const' não existe na gramática", "program p; const c = 1; begin x := 1; end.",
                "Erro sintático na linha 1: Encontrado 'const'; esperado 'begin', 'var' ou 'procedure'");
        checarSaida("'/' não existe na gramática", "program p; begin x := a / b; end.",
                "Erro sintático na linha 1: Encontrado '/'; esperado ';', '=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div' ou 'mod'");
        checarSaida("'and' não existe na gramática", "program p; begin if a and b then x := 1; end.",
                "Erro sintático na linha 1: Encontrado 'and'; esperado 'then', '=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div' ou 'mod'");
        checarSaida("parênteses vazios na declaração do procedimento", "program p; procedure q(); begin x := 1; end; begin q; end.",
                "Erro sintático na linha 1: Encontrado ')'; esperado identificador ou 'var'");
        checarSaida("'=' no lugar de ':='", "program p; begin x = 1; end.",
                "Erro sintático na linha 1: Encontrado '='; esperado ';', '(' ou ':='");
        checarSaida("atribuição sem expressão", "program p; begin x := ; end.",
                "Erro sintático na linha 1: Encontrado ';'; esperado identificador, '(', '+', '-', número, constante string ou constante char");
        checarSaida("'while' sem comando", "program p; begin while x do; end.",
                "Erro sintático na linha 1: Encontrado ';'; esperado identificador, 'begin', 'if' ou 'while'");
        checarSaida("erro na linha 5 de um programa em várias linhas", "program p;\nvar x: integer;\nbegin\n  x := 1;\n  x := x +;\nend.",
                "Erro sintático na linha 5: Encontrado ';'; esperado identificador, '(', número, constante string ou constante char");
        checarSaida("texto depois do '.' final", "program p; begin x := 1; end. x",
                "Erro sintático na linha 1: Encontrado identificador 'x'; esperado fim do arquivo");
        checarSaida("'var' sem nome de parâmetro", "program p; procedure q(var: integer); begin x := 1; end; begin q; end.",
                "Erro sintático na linha 1: Encontrado ':'; esperado identificador");
        checarSaida("argumento faltando depois da vírgula", "program p; begin q(a,); end.",
                "Erro sintático na linha 1: Encontrado ')'; esperado identificador, '(', '+', '-', número, constante string ou constante char");
        checarSaida("';' no lugar do '.' final", "program p; begin x := 1; end;",
                "Erro sintático na linha 1: Encontrado ';'; esperado '.'");
        checarSaida("só erro léxico (programa correto sem o '@')", "program p; begin x := 1; @\nend.",
                "Erro léxico na linha 1: Caractere inválido: @");
        checarSaida("falta ';' depois do nome do programa (erro na linha 2)", "program p\nbegin x := 1; end.",
                "Erro sintático na linha 2: Encontrado 'begin'; esperado ';'");
        checarSaida("arquivo vazio", "",
                "Erro sintático na linha 1: Fim do arquivo inesperado; esperado 'program'");
        checarSaida("falta ';' depois do tipo", "program p; var x: integer begin x := 1; end.",
                "Erro sintático na linha 1: Encontrado 'begin'; esperado ';'");
        checarSaida("'then' sem comando antes do 'else'", "program p; begin if x then else x := 1; end.",
                "Erro sintático na linha 1: Encontrado 'else'; esperado identificador, 'begin', 'if' ou 'while'");
        // A recuperação insere o 'begin' que falta; o "begin q; end" passa a ser o
        // fim do corpo do procedimento, e falta então o bloco do programa principal
        // (o '.' chega onde se esperava o ';' depois do procedimento).
        checarSaida("procedimento sem 'begin'", "program p; procedure q; x := 1; begin q; end.",
                "Erro sintático na linha 1: Encontrado identificador 'x'; esperado 'begin', 'var' ou 'procedure'\n"
                + "Erro sintático na linha 1: Encontrado '.'; esperado ';'");
        checarSaida("declaração sem nome de variável", "program p; var : integer; begin x := 1; end.",
                "Erro sintático na linha 1: Encontrado ':'; esperado identificador");
    }

    private static void verificarProgramasDaUnidadeI() {
        checarSaida("primeiro exemplo do enunciado da Unidade I (usa const e ; antes de else)", linhas(
                "program teste;",
                "var x,y: integer;",
                "const pi := 3.1416;",
                "/* inicio do programa */",
                "begin",
                "read(x);",
                "if (x > y) then",
                "y := x ;",
                "else",
                "y := -x;",
                "writeln(x);",
                "write(y);",
                "end."),
                // const não existe no Mini_Pascal; o ';' antes do else também é erro.
                "Erro sintático na linha 3: Encontrado 'const'; esperado identificador, 'begin', 'var' ou 'procedure'\n"
                + "Erro sintático na linha 9: Encontrado 'else'; esperado identificador, 'begin', 'end', 'if' ou 'while'");
        checarSaida("segundo exemplo do enunciado da Unidade I (usa for e /)", linhas(
                "Program Piloto;",
                "    /* declaracoes de variaveis e constantes globais */",
                "      var cont, total: integer ;",
                "Nota1, Nota2, Media_das_medias, med: real;",
                "/* Inicio do Programa */",
                "begin",
                "media_das_medias := 0;",
                "writeln(\"******** ENTRADA DE DADOS ***************\");",
                "writeln(\"Digite o total de alunos\");",
                "read(total);",
                "for cont=1 to total do",
                "begin",
                "writeln(\"Digite os valores da primeira nota do aluno \", cont);",
                "read(Nota1);",
                "writeln(\"Digite os valores da segunda nota do aluno \", cont);",
                "read(Nota2);",
                "med := (Nota1+Nota2)/2.0;",
                "media_das_medias := media_das_medias + med;",
                "write(\"Media = \",med);",
                "end;",
                "write(\"Media Geral = \", Media_das_medias/total);",
                "end."),
                // for não existe no Mini_Pascal, nem o operador '/' (aparece duas vezes).
                "Erro sintático na linha 11: Encontrado 'for'; esperado identificador, 'begin', 'end', 'if' ou 'while'\n"
                + "Erro sintático na linha 17: Encontrado '/'; esperado ';', '=', '<', '>', '<>', '<=', '>=', '+', '-', "
                + "'*', 'div' ou 'mod'\n"
                + "Erro sintático na linha 21: Encontrado '/'; esperado ',', ')', '=', '<', '>', '<>', '<=', '>=', '+', '-', "
                + "'*', 'div' ou 'mod'");
        checarSaida("programa demo da apresentação da Unidade I", linhas(
                "/* demonstracao */",
                "Program Demo;",
                "var x: integer;",
                "    media: real;",
                "    letra: char;",
                "begin",
                "  x := 10 mod 3;",
                "  media := (x + 2.5) / 2;",
                "  letra := 'a';",
                "  if (x >= 1) and not (media <> 3.0e-2) then",
                "    writeln(\"Media = \", media);",
                "  x := 5 @ 2;",
                "  writeln(\"sem fechar);",
                "end."),
                // '/' e 'and' não existem no Mini_Pascal. Sem o '@' (erro léxico), sobra
                // "5 2", que é erro sintático. A string não fechada vai até o fim da
                // linha 13 (erro léxico); sem ela, sobra "writeln(" e o 'end' da linha 14
                // chega onde se esperava o argumento.
                "Erro sintático na linha 8: Encontrado '/'; esperado ';', '=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div' ou 'mod'" + "\n"
                + "Erro sintático na linha 10: Encontrado 'and'; esperado 'then', '=', '<', '>', '<>', '<=', '>=', '+', '-', "
                + "'*', 'div' ou 'mod'\n"
                + "Erro léxico na linha 12: Caractere inválido: @" + "\n"
                + "Erro sintático na linha 12: Encontrado número '2'; esperado ';', '=', '<', '>', '<>', '<=', '>=', '+', '-', "
                + "'*', 'div' ou 'mod'\n"
                + "Erro léxico na linha 13: String não fechada (falta \"): \"sem fechar);\n"
                + "Erro sintático na linha 14: Encontrado 'end'; esperado identificador, '(', '+', '-', número, "
                + "constante string ou constante char");
    }

    // ---------------------------------------------------- programas gerados

    private static void verificarGerados() {
        Gramatica g = TABELA.getGramatica();
        Random rnd = new Random(2026);
        int aceitos = 0;
        String recusado = null;
        for (int i = 0; i < 1000; i++) {
            String fonte = gerarFonte(g, "PROG", 0, rnd);
            ResultadoAnalise r = ANALISADOR.analisar(fonte);
            if (r.isSucesso()) {
                aceitos++;
            } else if (recusado == null) {
                recusado = fonte + " -> " + r.formatar();
            }
        }
        checar("1000 programas gerados a partir da gramática, todos aceitos"
                + (recusado == null ? "" : " (recusado: " + recusado + ")"), aceitos == 1000);
    }

    private static String gerarFonte(Gramatica g, String simbolo, int profundidade, Random rnd) {
        if (g.ehTerminal(simbolo)) {
            return lexemaDe(simbolo, rnd);
        }
        List<Producao> opcoes = g.producoesDe(simbolo);
        Producao escolhida = opcoes.get(rnd.nextInt(opcoes.size()));
        if (profundidade > 8) {
            for (Producao p : opcoes) {
                if (naoTerminais(g, p) < naoTerminais(g, escolhida)) {
                    escolhida = p;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : escolhida.getCorpo()) {
            sb.append(gerarFonte(g, s, profundidade + 1, rnd)).append(rnd.nextInt(4) == 0 ? "\n" : " ");
        }
        return sb.toString();
    }

    private static int naoTerminais(Gramatica g, Producao p) {
        int n = 0;
        for (String s : p.getCorpo()) {
            if (!g.ehTerminal(s)) {
                n++;
            }
        }
        return n;
    }

    private static String lexemaDe(String terminal, Random rnd) {
        switch (terminal) {
            case Terminal.ID:
                return new String[]{"x", "total", "Media_2", "writeln", "READ"}[rnd.nextInt(5)];
            case Terminal.NUMERO:
                return new String[]{"0", "42", "3.14", "1.5e-3"}[rnd.nextInt(4)];
            case Terminal.TIPO:
                return new String[]{"integer", "real", "char", "String"}[rnd.nextInt(4)];
            case Terminal.CONSTANTE_STRING:
                return "\"texto\"";
            case Terminal.CONSTANTE_CHAR:
                return "'c'";
            default:
                return rnd.nextInt(5) == 0 ? terminal.toUpperCase() : terminal;
        }
    }

    // ------------------------------------------------------ programas grandes

    private static void verificarGrandes() {
        StringBuilder grande = new StringBuilder("program grande;\nvar a, b, c: integer;\nbegin\n");
        for (int i = 0; i < 20000; i++) {
            grande.append("  a := (a + ").append(i).append(") * b div 3 - c mod 7;\n");
        }
        grande.append("  x := 1 +;\nend.\n");
        ResultadoAnalise r = ANALISADOR.analisar(grande.toString());
        checar("programa de 20 mil comandos: erro detectado na linha 20004",
                r.getErros().size() == 1 && r.getErros().get(0).getLinha() == 20004);

        int n = 3000;
        StringBuilder aninhado = new StringBuilder("program n; begin x := ");
        for (int i = 0; i < n; i++) {
            aninhado.append('(');
        }
        aninhado.append('1');
        for (int i = 0; i < n; i++) {
            aninhado.append(')');
        }
        aninhado.append("; ");
        for (int i = 0; i < n; i++) {
            aninhado.append("if a then ");
        }
        aninhado.append("x := 1; ");
        for (int i = 0; i < n; i++) {
            aninhado.append("begin ");
        }
        aninhado.append("x := 1; ");
        for (int i = 0; i < n; i++) {
            aninhado.append("end; ");
        }
        aninhado.append("end.");
        ResultadoAnalise r2 = ANALISADOR.analisar(aninhado.toString());
        checar("3000 níveis de parênteses, de if e de begin/end: OK, sem estouro de pilha", r2.isSucesso());
    }

    // ---------------------------------------------------------- linha de comando

    private static void verificarLinhaDeComando() {
        try {
            Path pasta = Files.createTempDirectory("tokenflow_completo");
            Path ok = pasta.resolve("ok.txt");
            Path errado = pasta.resolve("errado.txt");
            Path saida = pasta.resolve("saida.txt");
            String programaOk = "program p;\nvar x: integer;\nbegin\n  x := 1;\nend.\n";
            Files.write(ok, programaOk.getBytes(StandardCharsets.UTF_8));
            Files.write(errado, "program p;\nbegin\n  x := 1 +;\n  y := @;\nend.\n".getBytes(StandardCharsets.UTF_8));

            String[] console = new String[1];
            int semArgumentos = executarCalado(new String[0], console);
            checar("sem argumentos: código 1 e mensagem de uso", semArgumentos == 1 && console[0].startsWith("Uso:"));

            int inexistente = executarCalado(new String[]{pasta.resolve("nao_existe.txt").toString()}, console);
            checar("arquivo inexistente: código 1", inexistente == 1 && console[0].startsWith("Arquivo nao encontrado"));

            int codigoOk = executarCalado(new String[]{ok.toString(), saida.toString()}, console);
            checar("programa correto: código 0, imprime OK e grava OK",
                    codigoOk == 0 && console[0].trim().equals("OK") && ler(saida).equals("OK\n"));

            int codigoErro = executarCalado(new String[]{errado.toString(), saida.toString()}, console);
            String esperado = "Erro sintático na linha 3: Encontrado ';'; esperado identificador, '(', número, "
                    + "constante string ou constante char\n"
                    + "Erro léxico na linha 4: Caractere inválido: @\n";
            checar("programa com erros: código 1 e arquivo em UTF-8 com acentos (sintático e léxico)",
                    codigoErro == 1 && ler(saida).equals(esperado));
            checar("no console, as mesmas mensagens sem acentos",
                    console[0].replace("\r", "").equals("Erro sintatico na linha 3: Encontrado ';'; esperado "
                            + "identificador, '(', numero, constante string ou constante char\n"
                            + "Erro lexico na linha 4: Caractere invalido: @\n"));

            int mesmo = executarCalado(new String[]{ok.toString(), ok.toString()}, console);
            checar("saída igual à entrada é recusada e a entrada fica intacta",
                    mesmo == 1 && ler(ok).equals(programaOk));

            for (Path p : new Path[]{ok, errado, saida}) {
                Files.deleteIfExists(p);
            }
            Files.deleteIfExists(pasta);
        } catch (IOException e) {
            checar("linha de comando: erro de E/S no teste: " + e.getMessage(), false);
        }
    }

    private static int executarCalado(String[] args, String[] console) {
        PrintStream original = System.out;
        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(capturado, true));
            return AnaliseSintatica.executar(args);
        } finally {
            System.setOut(original);
            console[0] = new String(capturado.toByteArray());
        }
    }

    private static String ler(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ apoio

    private static String linhas(String... linhas) {
        return String.join("\n", linhas);
    }

    // Junta os valores com espaço; cada comando if fica entre colchetes.
    private static final class AcoesEstrutura implements AcoesReducao {
        @Override
        public Object aoEmpilhar(Terminal terminal) {
            return terminal.getLexema();
        }

        @Override
        public Object aoReduzir(Producao p, List<Object> valores, int linha) {
            List<String> partes = new ArrayList<>();
            for (Object v : valores) {
                if (v != null && !v.toString().isEmpty()) {
                    partes.add(v.toString());
                }
            }
            String texto = String.join(" ", partes);
            return !p.getCorpo().isEmpty() && p.getCorpo().get(0).equals("if") ? "[" + texto + "]" : texto;
        }
    }

    private static void checarEstrutura(String fonte, String esperado) {
        ResultadoAnalise r = new AnalisadorSintatico(TABELA, new TabelaPalavrasReservadas(), new AcoesEstrutura())
                .analisar(fonte);
        boolean ok = r.isSucesso() && esperado.equals(r.getValor());
        checar("else com o if mais próximo: " + fonte + (ok ? "" : " (obtido: " + r.getValor() + ")"), ok);
    }

    private static void checarOk(String descricao, String fonte) {
        ResultadoAnalise r = ANALISADOR.analisar(fonte);
        checar("válido: " + descricao + (r.isSucesso() ? "" : " -> " + r.formatar()),
                r.isSucesso() && r.formatar().equals("OK"));
    }

    private static void checarSaida(String descricao, String fonte, String esperado) {
        String obtido = ANALISADOR.analisar(fonte).formatar();
        boolean ok = obtido.equals(esperado);
        checar(descricao + (ok ? "" : " (obtido: " + obtido + ")"), ok);
    }

    private static void executar(String parte, Runnable verificacoes) {
        try {
            verificacoes.run();
        } catch (RuntimeException e) {
            checar(parte + ": exceção inesperada " + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
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
