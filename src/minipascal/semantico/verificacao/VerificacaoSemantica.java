package minipascal.semantico.verificacao;

import minipascal.cli.AnaliseSemantica;
import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.semantico.AnalisadorSemantico;
import minipascal.semantico.Compilador;
import minipascal.semantico.ResultadoCompilacao;
import minipascal.semantico.Simbolo;
import minipascal.semantico.Tipo;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.LeitorGramatica;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.slr.TabelaSLR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Verifica a análise semântica (AnalisadorSemantico, pelo Compilador):
 *
 *   - as regras cobrem exatamente as produções de gramatica.txt;
 *   - programas corretos dão OK;
 *   - cada verificação, com a mensagem exata e a linha: declarações, nomes e
 *     categorias, tipos, chamadas e os procedimentos predefinidos;
 *   - escopos: nomes locais escondem os de fora, procedimentos aninhados,
 *     recursão, procedimento declarado depois não é visível;
 *   - sem cascatas: expressão com erro não gera novos erros; nome não
 *     declarado é relatado uma vez por escopo;
 *   - convivência com as outras fases: a semântica para no primeiro erro
 *     sintático; erros das três fases na ordem do texto; erros só léxicos não
 *     param a semântica;
 *   - a tabela de símbolos montada e a linha de comando AnaliseSemantica;
 *   - um gerador de programas bem tipados (que sabe o tipo de cada expressão,
 *     sem usar o analisador) dá OK em todos; com um erro injetado de cada
 *     tipo, a saída tem de ser exatamente aquele erro, naquela linha;
 *   - as ações semânticas não mudam a análise sintática: em textos
 *     aleatórios, os erros léxicos e sintáticos são os mesmos do analisador
 *     sem ações, e nenhum erro semântico vem depois do primeiro sintático.
 */
public class VerificacaoSemantica {

    private static int total = 0;
    private static int falhas = 0;

    private static Compilador COMPILADOR;

    public static void main(String[] args) {
        try {
            COMPILADOR = new Compilador();
        } catch (RuntimeException e) {
            checar("o compilador é montado (gramática e semântica): " + e, false);
            terminar();
        }
        executar("cobertura da gramática", VerificacaoSemantica::verificarCobertura);
        executar("programas corretos", VerificacaoSemantica::verificarCorretos);
        executar("declarações", VerificacaoSemantica::verificarDeclaracoes);
        executar("nomes e categorias", VerificacaoSemantica::verificarNomes);
        executar("tipos", VerificacaoSemantica::verificarTipos);
        executar("chamadas", VerificacaoSemantica::verificarChamadas);
        executar("escopos", VerificacaoSemantica::verificarEscopos);
        executar("cascatas", VerificacaoSemantica::verificarCascatas);
        executar("outras fases", VerificacaoSemantica::verificarOutrasFases);
        executar("tabela de símbolos", VerificacaoSemantica::verificarTabela);
        executar("linha de comando", VerificacaoSemantica::verificarLinhaDeComando);
        executar("programas gerados", VerificacaoSemantica::verificarGerados);
        executar("textos aleatórios", VerificacaoSemantica::verificarAleatorios);
        terminar();
    }

    private static final String SEM = "Erro semântico na linha ";

    // ----------------------------------------------------------- cobertura

    private static void verificarCobertura() {
        Gramatica g = AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA).getGramatica();
        boolean montou;
        try {
            new AnalisadorSemantico(g);
            montou = true;
        } catch (IllegalArgumentException e) {
            montou = false;
        }
        checar("toda produção de gramatica.txt tem regra semântica, e toda regra, produção", montou);

        // Uma gramática com uma produção a mais (e outra a menos) é recusada.
        String texto = textoDaGramaticaCompleta()
                .replace("FATOR -> numero | id", "FATOR -> numero | id | nil");
        String mensagem = "";
        try {
            new AnalisadorSemantico(LeitorGramatica.lerTexto(texto));
        } catch (IllegalArgumentException e) {
            mensagem = e.getMessage();
        }
        checar("produção sem regra é recusada, com o nome dela (" + mensagem + ")",
                mensagem.contains("sem regra: FATOR -> nil"));
        String sem = textoDaGramaticaCompleta()
                .replace("| constante_char", "");
        mensagem = "";
        try {
            new AnalisadorSemantico(LeitorGramatica.lerTexto(sem));
        } catch (IllegalArgumentException e) {
            mensagem = e.getMessage();
        }
        checar("regra sem produção é recusada, com o nome dela (" + mensagem + ")",
                mensagem.contains("regra sem produção: FATOR -> constante_char"));
    }

    // ---------------------------------------------------- programas corretos

    private static void verificarCorretos() {
        checarSaida("todos os tipos, procedimento com parâmetro var, read/write/writeln", linhas(
                "program exemplo;",
                "var a, b: integer;",
                "    r: real;",
                "    c: char;",
                "    s: string;",
                "procedure soma(var res: integer; x: integer);",
                "var t: integer;",
                "begin",
                "  t := x * 2;",
                "  res := res + t;",
                "end;",
                "begin",
                "  read(a, b);",
                "  r := a + 0.5;",
                "  s := c;",
                "  s := s + \"!\" + c;",
                "  if (a > b) = (b > a) then writeln(\"igual\") else writeln(s);",
                "  while a <> 0 do soma(b, a div 2);",
                "  write(a mod 3, r, s);",
                "  writeln;",
                "end."), "OK");
        checarSaida("recursão: o procedimento chama a si mesmo", linhas(
                "program p;",
                "var x: integer;",
                "procedure fat(n: integer; var r: integer);",
                "begin",
                "  if n <= 1 then r := 1 else begin fat(n - 1, r); r := r * n; end;",
                "end;",
                "begin",
                "  fat(5, x);",
                "  writeln(x);",
                "end."), "OK");
        checarSaida("conversões permitidas: integer em real, char em string, também em argumentos", linhas(
                "program p;",
                "var r: real; s: string; c: char; i: integer;",
                "procedure q(x: real; t: string);",
                "begin",
                "  writeln(x, t);",
                "end;",
                "begin",
                "  r := 1;",
                "  r := i * 2 + r;",
                "  s := 'a';",
                "  s := c + c;",
                "  q(i, c);",
                "  q(3, \"txt\");",
                "end."), "OK");
        checarSaida("comparações: números entre si, textos entre si, lógicos entre si", linhas(
                "program p;",
                "var i: integer; r: real; c: char; s: string;",
                "begin",
                "  if i < r then writeln;",
                "  if c = s then writeln;",
                "  if \"abc\" <= s then writeln;",
                "  while (i > 0) <> (r > 0) do i := i - 1;",
                "  writeln(i >= 1);",
                "end."), "OK");
        checarSaida("parâmetros por valor podem receber atribuição (são cópias locais)", linhas(
                "program p;",
                "procedure q(n: integer);",
                "begin",
                "  n := n + 1;",
                "end;",
                "begin",
                "  q(1);",
                "end."), "OK");
    }

    // ---------------------------------------------------------- declarações

    private static void verificarDeclaracoes() {
        checarSaida("variável declarada duas vezes na mesma lista", linhas(
                "program p;", "var x, x: integer;", "begin", "  x := 1;", "end."),
                SEM + "2: 'x' já foi declarado neste escopo (variável, linha 2)");
        checarSaida("variável declarada de novo em outra declaração (e com outro tipo)", linhas(
                "program p;", "var x: integer;", "    x: real;", "begin", "  x := 1;", "end."),
                SEM + "3: 'x' já foi declarado neste escopo (variável, linha 2)");
        checarSaida("parâmetro repetido e variável local com o nome de um parâmetro", linhas(
                "program p;", "procedure q(a: integer; a: real);", "var a: char;", "begin", "  writeln;", "end;",
                "begin", "  q(1, 2);", "end."),
                SEM + "2: 'a' já foi declarado neste escopo (parâmetro, linha 2)",
                SEM + "3: 'a' já foi declarado neste escopo (parâmetro, linha 2)");
        checarSaida("procedimento declarado duas vezes", linhas(
                "program p;", "procedure q;", "begin", "  writeln;", "end;", "procedure q;", "begin", "  writeln;",
                "end;", "begin", "  q;", "end."),
                SEM + "6: 'q' já foi declarado neste escopo (procedimento, linha 2)");
        checarSaida("procedimento com o nome de uma variável global", linhas(
                "program p;", "var q: integer;", "procedure q;", "begin", "  writeln;", "end;", "begin", "  q := 1;",
                "end."),
                SEM + "3: 'q' já foi declarado neste escopo (variável, linha 2)");
        checarSaida("maiúsculas e minúsculas: Total e TOTAL são o mesmo nome", linhas(
                "program p;", "var Total: integer;", "    TOTAL: real;", "begin", "  total := 1;", "end."),
                SEM + "3: 'TOTAL' já foi declarado neste escopo (variável, linha 2)");
        checarSaida("o nome do programa e os predefinidos podem ser redeclarados (ficam fora do programa)", linhas(
                "program p;", "var p, read: integer;", "begin", "  p := 1;", "  read := p;", "end."), "OK");
        checarSaida("parâmetro com o nome do próprio procedimento é aceito (esconde o procedimento)", linhas(
                "program p;", "procedure q(q: integer);", "begin", "  q := q + 1;", "end;", "begin", "  q(1);",
                "end."), "OK");
    }

    // ---------------------------------------------------- nomes e categorias

    private static void verificarNomes() {
        checarSaida("variável não declarada (numa expressão e como destino)", linhas(
                "program p;", "var x: integer;", "begin", "  x := y;", "  z := 1;", "end."),
                SEM + "4: Identificador 'y' não declarado",
                SEM + "5: Identificador 'z' não declarado");
        checarSaida("procedimento não declarado", linhas(
                "program p;", "begin", "  imprimir(1);", "end."),
                SEM + "3: Identificador 'imprimir' não declarado");
        checarSaida("procedimento usado numa expressão", linhas(
                "program p;", "var x: integer;", "procedure q;", "begin", "  writeln;", "end;", "begin",
                "  x := q + 1;", "end."),
                SEM + "8: 'q' é um procedimento e não pode ser usado em uma expressão");
        checarSaida("procedimento predefinido usado numa expressão", linhas(
                "program p;", "begin", "  writeln(write);", "end."),
                SEM + "3: 'write' é um procedimento predefinido e não pode ser usado em uma expressão");
        checarSaida("nome do programa usado numa expressão", linhas(
                "program teste;", "var x: integer;", "begin", "  x := teste;", "end."),
                SEM + "4: 'teste' é o nome do programa e não pode ser usado em uma expressão");
        checarSaida("atribuição a procedimento, a predefinido e ao nome do programa", linhas(
                "program p;", "procedure q;", "begin", "  writeln;", "end;", "begin", "  q := 1;", "  writeln := 2;",
                "  p := 3;", "end."),
                SEM + "7: Não é possível atribuir um valor a 'q', que é um procedimento",
                SEM + "8: Não é possível atribuir um valor a 'writeln', que é um procedimento predefinido",
                SEM + "9: Não é possível atribuir um valor a 'p', que é o nome do programa");
        checarSaida("variável, parâmetro e nome do programa chamados como procedimento", linhas(
                "program p;", "var x: integer;", "procedure q(var r: real; n: char);", "begin", "  r;", "  n(1);",
                "end;", "begin", "  x;", "  p;", "end."),
                SEM + "5: 'r' é um parâmetro var do tipo real e não pode ser chamado como procedimento",
                SEM + "6: 'n' é um parâmetro do tipo char e não pode ser chamado como procedimento",
                SEM + "9: 'x' é uma variável do tipo integer e não pode ser chamado como procedimento",
                SEM + "10: 'p' é o nome do programa e não pode ser chamado como procedimento");
    }

    // ----------------------------------------------------------------- tipos

    private static void verificarTipos() {
        checarSaida("atribuições incompatíveis", linhas(
                "program p;", "var i: integer; r: real; c: char; s: string;", "begin",
                "  i := r;", "  i := 2.5;", "  i := 'a';", "  r := \"x\";", "  c := s;", "  c := \"a\";",
                "  s := 1;", "  i := 1 < 2;", "end."),
                SEM + "4: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é real",
                SEM + "5: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é real",
                SEM + "6: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é char",
                SEM + "7: Tipos incompatíveis na atribuição a 'r': a variável é real e a expressão é string",
                SEM + "8: Tipos incompatíveis na atribuição a 'c': a variável é char e a expressão é string",
                SEM + "9: Tipos incompatíveis na atribuição a 'c': a variável é char e a expressão é string",
                SEM + "10: Tipos incompatíveis na atribuição a 's': a variável é string e a expressão é integer",
                SEM + "11: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é lógico");
        checarSaida("tipo do resultado: integer op integer é integer; com real, é real", linhas(
                "program p;", "var i: integer;", "begin", "  i := 1 + 2 * 3 - 4;", "  i := 1 + 2.0;", "  i := 2 * 0.5;",
                "  i := -1.5;", "end."),
                SEM + "5: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é real",
                SEM + "6: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é real",
                SEM + "7: Tipos incompatíveis na atribuição a 'i': a variável é integer e a expressão é real");
        checarSaida("div e mod só com integer", linhas(
                "program p;", "var i: integer; r: real;", "begin", "  i := r div 2;", "  i := 7 mod r;",
                "  i := 7 div 2 mod 3;", "end."),
                SEM + "4: O operador 'div' exige operandos integer, mas recebeu real e integer",
                SEM + "5: O operador 'mod' exige operandos integer, mas recebeu integer e real");
        checarSaida("+ - * com textos e lógicos; + junta só textos", linhas(
                "program p;", "var i: integer; s: string; c: char;", "begin", "  i := i + s;", "  s := s - c;",
                "  i := c * 2;", "  i := (1 < 2) + 1;", "  s := c + 1;", "end."),
                SEM + "4: O operador '+' não se aplica aos tipos integer e string",
                SEM + "5: O operador '-' não se aplica aos tipos string e char",
                SEM + "6: O operador '*' não se aplica aos tipos char e integer",
                SEM + "7: O operador '+' não se aplica aos tipos lógico e integer",
                SEM + "8: O operador '+' não se aplica aos tipos char e integer");
        checarSaida("sinal só com números", linhas(
                "program p;", "var i: integer; s: string;", "begin", "  i := -s;", "  i := +'a';", "  i := -(1 > 0);",
                "end."),
                SEM + "4: O sinal '-' não se aplica ao tipo string",
                SEM + "5: O sinal '+' não se aplica ao tipo char",
                SEM + "6: O sinal '-' não se aplica ao tipo lógico");
        checarSaida("comparações entre tipos incompatíveis", linhas(
                "program p;", "var i: integer; s: string;", "begin", "  if i < s then writeln;",
                "  if 'a' = 1 then writeln;", "  if (i > 0) = i then writeln;", "end."),
                SEM + "4: Comparação entre tipos incompatíveis: integer e string",
                SEM + "5: Comparação entre tipos incompatíveis: char e integer",
                SEM + "6: Comparação entre tipos incompatíveis: lógico e integer");
        checarSaida("condição de if e de while que não é lógica", linhas(
                "program p;", "var i: integer; s: string; r: real;", "begin", "  if i then writeln;",
                "  if i + 1 then writeln else writeln;", "  while s do writeln;", "  while r * 2 do writeln;",
                "  if (i) then writeln;", "end."),
                SEM + "4: A condição do 'if' deve ser lógica (uma comparação), mas é integer",
                SEM + "5: A condição do 'if' deve ser lógica (uma comparação), mas é integer",
                SEM + "6: A condição do 'while' deve ser lógica (uma comparação), mas é string",
                SEM + "7: A condição do 'while' deve ser lógica (uma comparação), mas é real",
                SEM + "8: A condição do 'if' deve ser lógica (uma comparação), mas é integer");
        checarSaida("condições de if e while aninhados conferidas cada uma no seu lugar", linhas(
                "program p;", "var i: integer;", "begin",
                "  while i > 0 do if i then while 1 do i := i - 1;", "end."),
                SEM + "4: A condição do 'if' deve ser lógica (uma comparação), mas é integer",
                SEM + "4: A condição do 'while' deve ser lógica (uma comparação), mas é integer");
    }

    // ---------------------------------------------------------------- chamadas

    private static final String PROC_Q = "procedure q(var r: integer; v: real);";

    private static void verificarChamadas() {
        checarSaida("número de argumentos diferente do de parâmetros", linhas(
                "program p;", "var x: integer;", PROC_Q, "begin", "  r := 1;", "end;",
                "procedure nada;", "begin", "  writeln;", "end;", "begin",
                "  q(x);", "  q(x, 1, 2);", "  q;", "  nada(1);", "end."),
                SEM + "12: O procedimento 'q' tem 2 parâmetros, mas a chamada passa 1 argumento",
                SEM + "13: O procedimento 'q' tem 2 parâmetros, mas a chamada passa 3 argumentos",
                SEM + "14: O procedimento 'q' tem 2 parâmetros, mas a chamada passa 0 argumentos",
                SEM + "15: O procedimento 'nada' tem 0 parâmetros, mas a chamada passa 1 argumento");
        checarSaida("argumento de parâmetro por valor com tipo errado", linhas(
                "program p;", "var x: integer;", PROC_Q, "begin", "  r := 1;", "end;", "begin",
                "  q(x, \"a\");", "  q(x, 1 < 2);", "end."),
                SEM + "8: Argumento 2 de 'q': o parâmetro 'v' é real e o argumento é string",
                SEM + "9: Argumento 2 de 'q': o parâmetro 'v' é real e o argumento é lógico");
        checarSaida("parâmetro var precisa receber uma variável", linhas(
                "program p;", "var x: integer;", PROC_Q, "begin", "  r := 1;", "end;", "begin",
                "  q(1, 1);", "  q(x + 0, 1);", "  q((x), 1);", "end."),
                SEM + "8: Argumento 1 de 'q': o parâmetro 'r' é var e precisa receber uma variável",
                SEM + "9: Argumento 1 de 'q': o parâmetro 'r' é var e precisa receber uma variável",
                SEM + "10: Argumento 1 de 'q': o parâmetro 'r' é var e precisa receber uma variável");
        checarSaida("parâmetro var exige o mesmo tipo (integer não serve para var real)", linhas(
                "program p;", "var i: integer; r: real;", "procedure dobra(var x: real);", "begin",
                "  x := x * 2;", "end;", "begin", "  dobra(i);", "  dobra(r);", "end."),
                SEM + "8: Argumento 1 de 'dobra': o parâmetro var 'x' é real e a variável 'i' é integer "
                        + "(num parâmetro var os tipos devem ser iguais)");
        checarSaida("parâmetros var e por valor de um procedimento podem ser repassados", linhas(
                "program p;", "procedure a(var x: integer);", "begin", "  x := 1;", "end;",
                "procedure b(var y: integer; z: integer);", "begin", "  a(y);", "  a(z);", "end;",
                "begin", "  writeln;", "end."), "OK");
        checarSaida("read: precisa de argumentos, e eles precisam ser variáveis", linhas(
                "program p;", "var x: integer; s: string;", "begin", "  read;", "  read(1);", "  read(x, s, x + 1);",
                "  read(x, s);", "end."),
                SEM + "4: 'read' precisa de pelo menos um argumento",
                SEM + "5: Argumento 1 de 'read' precisa ser uma variável",
                SEM + "6: Argumento 3 de 'read' precisa ser uma variável");
        checarSaida("write precisa de argumentos; writeln não; aceitam qualquer tipo", linhas(
                "program p;", "var x: integer;", "begin", "  write;", "  write(x, 1.5, 'c', \"s\", x > 1);",
                "  writeln;", "  writeln(x);", "end."),
                SEM + "4: 'write' precisa de pelo menos um argumento");
        checarSaida("predefinidos em qualquer caixa (READ, WriteLn); a mensagem usa o nome como escrito", linhas(
                "program p;", "var x: integer;", "begin", "  READ(x);", "  WriteLn(x);", "  Write;", "end."),
                SEM + "6: 'Write' precisa de pelo menos um argumento");
        checarSaida("erro dentro de um argumento não gera erro de argumento", linhas(
                "program p;", "var x: integer;", PROC_Q, "begin", "  r := 1;", "end;", "begin",
                "  q(y, 'a' * 2);", "end."),
                SEM + "8: Identificador 'y' não declarado",
                SEM + "8: O operador '*' não se aplica aos tipos char e integer");
    }

    // ----------------------------------------------------------------- escopos

    private static void verificarEscopos() {
        checarSaida("variável local esconde a global (com outro tipo)", linhas(
                "program p;", "var x: integer;", "procedure q;", "var x: string;", "begin", "  x := \"local\";",
                "end;", "begin", "  x := 1;", "  q;", "end."), "OK");
        checarSaida("procedimento aninhado vê parâmetros e variáveis de fora; de fora não se vê o aninhado", linhas(
                "program p;",
                "var x: integer; total: real;",
                "procedure externo(a: integer);",
                "var y: real;",
                "  procedure interno(var b: real);",
                "  begin",
                "    b := a + x + y;",
                "    total := b;",
                "  end;",
                "begin",
                "  interno(y);",
                "  interno(total);",
                "end;",
                "begin",
                "  externo(x);",
                "  interno(total);",
                "end."),
                SEM + "16: Identificador 'interno' não declarado");
        checarSaida("variável local não é vista no programa principal", linhas(
                "program p;", "procedure q;", "var local: integer;", "begin", "  local := 1;", "end;", "begin",
                "  local := 2;", "end."),
                SEM + "8: Identificador 'local' não declarado");
        checarSaida("procedimento declarado depois não é visível antes (não há declaração antecipada)", linhas(
                "program p;", "procedure a;", "begin", "  b;", "end;", "procedure b;", "begin", "  a;", "end;",
                "begin", "  a;", "  b;", "end."),
                SEM + "4: Identificador 'b' não declarado");
        checarSaida("escopos irmãos não se enxergam", linhas(
                "program p;", "procedure a;", "var x: integer;", "begin", "  x := 1;", "end;", "procedure b;",
                "begin", "  x := 2;", "end;", "begin", "  a;", "  b;", "end."),
                SEM + "9: Identificador 'x' não declarado");
        checarSaida("o tipo usado é o da declaração visível (a local, char)", linhas(
                "program p;", "var v: integer;", "procedure q;", "var v: char;", "begin", "  v := 1;", "end;",
                "begin", "  v := 1;", "end."),
                SEM + "6: Tipos incompatíveis na atribuição a 'v': a variável é char e a expressão é integer");
    }

    // ----------------------------------------------------------------- cascatas

    private static void verificarCascatas() {
        checarSaida("expressão com erro não gera mais erros (nem na atribuição)", linhas(
                "program p;", "var x: integer; s: string;", "begin", "  x := (s * 2 + 1) div 3 - 1;",
                "  if (s * 2) > 1 then writeln;", "end."),
                SEM + "4: O operador '*' não se aplica aos tipos string e integer",
                SEM + "5: O operador '*' não se aplica aos tipos string e integer");
        checarSaida("nome não declarado: uma mensagem só, mesmo usado várias vezes", linhas(
                "program p;", "var x: integer;", "begin", "  x := y + y * 2;", "  y := 1;", "  y;", "  read(y);",
                "end."),
                SEM + "4: Identificador 'y' não declarado");
        checarSaida("nome não declarado em procedimentos diferentes: uma mensagem em cada", linhas(
                "program p;", "procedure a;", "begin", "  w := 1;", "  w := 2;", "end;", "procedure b;", "begin",
                "  w := 3;", "end;", "begin", "  a;", "end."),
                SEM + "4: Identificador 'w' não declarado",
                SEM + "9: Identificador 'w' não declarado");
        checarSaida("chamada de procedimento não declarado não confere argumentos", linhas(
                "program p;", "begin", "  q(1, 'a' + 1);", "end."),
                SEM + "3: Identificador 'q' não declarado",
                SEM + "3: O operador '+' não se aplica aos tipos char e integer");
    }

    // ------------------------------------------------------------ outras fases

    private static void verificarOutrasFases() {
        ResultadoCompilacao r = COMPILADOR.analisar(linhas(
                "program p;", "var x: integer;", "begin", "  x := 1.5;", "  y := ;", "  z := 1;", "  x := 'a';",
                "end."));
        checar("erro semântico antes do primeiro erro sintático é relatado; depois dele, a semântica para",
                r.formatar().equals(String.join("\n",
                        SEM + "4: Tipos incompatíveis na atribuição a 'x': a variável é integer e a expressão é real",
                        "Erro sintático na linha 5: Encontrado ';'; esperado identificador, '(', '+', '-', número, "
                                + "constante string ou constante char"))
                        && !r.isSemanticaCompleta());
        checarSaida("só erros sintáticos, sem nenhum semântico depois deles", linhas(
                "program p;", "begin", "  x := 1 +;", "  y := \"a\" * 2;", "end."),
                "Erro sintático na linha 3: Encontrado ';'; esperado identificador, '(', número, constante string "
                        + "ou constante char");
        ResultadoCompilacao lexico = COMPILADOR.analisar(linhas(
                "program p;", "var x: integer;", "begin", "  x := 1 @;", "  x := 2.5;", "end."));
        checar("erro só léxico (o resto é um programa correto): a semântica continua",
                lexico.formatar().equals("Erro léxico na linha 4: Caractere inválido: @\n"
                        + SEM + "5: Tipos incompatíveis na atribuição a 'x': a variável é integer e a expressão é real")
                        && lexico.isSemanticaCompleta());
        checarSaida("três fases na mesma linha, na ordem do texto", linhas(
                "program p;", "var x: integer;", "begin", "  x := y; @ x := 1 +;", "end."),
                SEM + "4: Identificador 'y' não declarado",
                "Erro léxico na linha 4: Caractere inválido: @",
                "Erro sintático na linha 4: Encontrado ';'; esperado identificador, '(', número, constante string "
                        + "ou constante char");
        checarSaida("na mesma linha, a condição (antes) sai antes do erro do corpo", linhas(
                "program p;", "var x: integer;", "begin", "  if x then x := 1.5;", "end."),
                SEM + "4: A condição do 'if' deve ser lógica (uma comparação), mas é integer",
                SEM + "4: Tipos incompatíveis na atribuição a 'x': a variável é integer e a expressão é real");
        checarSaida("léxico entre dois erros semânticos da mesma linha", linhas(
                "program p;", "var x: integer;", "begin", "  x := y $ + 'a' * 2;", "end."),
                SEM + "4: Identificador 'y' não declarado",
                "Erro léxico na linha 4: Caractere inválido: $",
                SEM + "4: O operador '*' não se aplica aos tipos char e integer");
        ResultadoCompilacao ok = COMPILADOR.analisar("program p; begin writeln; end.");
        checar("programa correto: OK, sucesso, semântica completa",
                ok.formatar().equals("OK") && ok.isSucesso() && ok.isSemanticaCompleta() && ok.getErros().isEmpty());
        checar("os tokens lidos estão no resultado (para a interface)", ok.getTokens().size() == 8);
        ResultadoCompilacao comErro = COMPILADOR.analisar("program p; begin x := 1; end.");
        checar("erros da categoria semântica separáveis das outras",
                comErro.errosDa(CategoriaErro.SEMANTICO).size() == 1 && comErro.errosDa(CategoriaErro.SINTATICO).isEmpty());

        // A análise sintática sozinha (AnaliseSintatica) continua sem semântica.
        ResultadoAnalise soSintatica = new AnalisadorSintatico(
                AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA)).analisar(
                "program p; begin x := 1; end.");
        checar("o analisador sintático sem ações continua só sintático (OK com nome não declarado)",
                soSintatica.isSucesso());
    }

    // ----------------------------------------------------------- tabela de símbolos

    private static void verificarTabela() {
        ResultadoCompilacao r = COMPILADOR.analisar(linhas(
                "program exemplo;",
                "var a, b: integer;",
                "procedure soma(var res: integer; x, y: real);",
                "var t: char;",
                "  procedure aux;",
                "  begin",
                "    writeln;",
                "  end;",
                "begin",
                "  aux;",
                "end;",
                "begin",
                "  b := naoDeclarado;",
                "end."));
        String esperado = String.join("\n",
                "Escopo   Nível  Nome     Categoria                 Tipo     Linha  Parâmetros",
                "externo  0      read     procedimento predefinido  -        -",
                "externo  0      write    procedimento predefinido  -        -",
                "externo  0      writeln  procedimento predefinido  -        -",
                "externo  0      exemplo  programa                  -        1",
                "global   1      a        variável                  integer  2",
                "global   1      b        variável                  integer  2",
                "global   1      soma     procedimento              -        3      (var res: integer; x: real; y: real)",
                "soma     2      res      parâmetro var             integer  3",
                "soma     2      x        parâmetro                 real     3",
                "soma     2      y        parâmetro                 real     3",
                "soma     2      t        variável                  char     4",
                "soma     2      aux      procedimento              -        5") + "\n";
        String obtido = r.getTabelaSimbolos().listar();
        checar("tabela de símbolos do programa (escopos, níveis, tipos, parâmetros; sem o não declarado)"
                + (obtido.equals(esperado) ? "" : ":\n" + obtido), obtido.equals(esperado));
        Simbolo soma = r.getTabelaSimbolos().getHistorico().get(6);
        checar("o procedimento guarda os parâmetros, na ordem", soma.getNome().equals("soma")
                && soma.getParametros().size() == 3 && soma.getParametros().get(2).getTipo() == Tipo.REAL);
        ResultadoCompilacao parado = COMPILADOR.analisar("program p; var a: integer; begin a := ; end.");
        checar("com erro sintático, a tabela tem o que foi declarado até ali",
                !parado.isSemanticaCompleta() && parado.getTabelaSimbolos().getHistorico().size() == 5);
    }

    // ------------------------------------------------------------- linha de comando

    private static void verificarLinhaDeComando() {
        Path pasta;
        try {
            pasta = Files.createTempDirectory("tokenflow_semantica");
        } catch (IOException e) {
            checar("pasta temporária para os testes da linha de comando: " + e, false);
            return;
        }
        try {
            Path certo = pasta.resolve("certo.txt");
            Path errado = pasta.resolve("errado.txt");
            Path saida = pasta.resolve("saida.txt");
            Files.write(certo, "program p;\nvar x: integer;\nbegin\n  x := 1;\nend.\n".getBytes(StandardCharsets.UTF_8));
            Files.write(errado, "program p;\nvar x: integer;\nbegin\n  x := 2.5;\n  y := 1;\nend.\n"
                    .getBytes(StandardCharsets.UTF_8));

            String[] r = rodar(certo.toString());
            checar("programa correto: imprime OK e sai com 0", r[0].equals("0") && r[1].equals("OK\n"));
            r = rodar(errado.toString(), saida.toString());
            String esperado = "Erro semântico na linha 4: Tipos incompatíveis na atribuição a 'x': a variável é integer "
                    + "e a expressão é real\nErro semântico na linha 5: Identificador 'y' não declarado";
            checar("programa com erros: console sem acentos, código 1",
                    r[0].equals("1") && r[1].equals(semAcentos(esperado) + "\n"));
            String gravado = new String(Files.readAllBytes(saida), StandardCharsets.UTF_8);
            checar("arquivo de saída em UTF-8, com acentos", gravado.equals(esperado + "\n"));

            r = rodar("--tabela", certo.toString());
            checar("--tabela: resultado e depois a tabela de símbolos",
                    r[0].equals("0") && r[1].startsWith("OK\n\nTabela de simbolos:\nEscopo")
                            && r[1].contains("global   1      x        variavel"));
            Path parado = pasta.resolve("parado.txt");
            Files.write(parado, "program p; var a: integer; begin a := ; end.".getBytes(StandardCharsets.UTF_8));
            r = rodar(parado.toString(), "--tabela");
            checar("--tabela em qualquer posição; avisa quando a semântica parou num erro sintático",
                    r[0].equals("1") && r[1].contains("analise semantica parou no primeiro erro sintatico"));
            checar("sem argumentos: mostra o uso", rodar()[1].startsWith("Uso: java -cp TokenFlow.jar "
                    + "minipascal.cli.AnaliseSemantica"));
            checar("arquivo que não existe", rodar(pasta.resolve("nao_existe.txt").toString())[1]
                    .startsWith("Arquivo nao encontrado"));
            checar("saída igual à entrada é recusada", rodar(certo.toString(), certo.toString())[1]
                    .startsWith("O arquivo de saida nao pode ser o mesmo da entrada"));
            checar("argumentos demais: mostra o uso", rodar("a", "b", "c")[1].startsWith("Uso:"));
        } catch (IOException e) {
            checar("arquivos da linha de comando: " + e, false);
        }
    }

    // Roda AnaliseSemantica.executar com o console capturado: {código, texto}.
    private static String[] rodar(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        int codigo;
        try {
            System.setOut(new PrintStream(capturado, true, "UTF-8"));
            codigo = AnaliseSemantica.executar(args);
        } catch (IOException e) {
            codigo = -1;
        } finally {
            System.setOut(original);
        }
        return new String[]{String.valueOf(codigo),
                new String(capturado.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n")};
    }

    private static String semAcentos(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // --------------------------------------------------------- programas gerados

    private static void verificarGerados() {
        Random rnd = new Random(2026);
        int corretos = 0;
        String exemploErrado = null;
        int quantos = 1500;
        for (int i = 0; i < quantos; i++) {
            GeradorDeProgramas g = new GeradorDeProgramas(rnd);
            String programa = g.gerar(null);
            String saida = COMPILADOR.analisar(programa).formatar();
            if (saida.equals("OK")) {
                corretos++;
            } else if (exemploErrado == null) {
                exemploErrado = programa + "\n-> " + saida;
            }
        }
        checar(quantos + " programas gerados bem tipados: todos OK"
                + (exemploErrado == null ? "" : " (ex.:\n" + exemploErrado + ")"), corretos == quantos);

        for (GeradorDeProgramas.Defeito defeito : GeradorDeProgramas.Defeito.values()) {
            int certos = 0;
            int tentativas = 0;
            String exemplo = null;
            while (tentativas < 150) {
                GeradorDeProgramas g = new GeradorDeProgramas(rnd);
                String programa = g.gerar(defeito);
                if (programa == null) {
                    continue; // o programa sorteado não tinha onde pôr esse defeito
                }
                tentativas++;
                String esperado = SEM + g.getLinhaDoDefeito() + ": " + g.getMensagemDoDefeito();
                String saida = COMPILADOR.analisar(programa).formatar();
                if (saida.equals(esperado)) {
                    certos++;
                } else if (exemplo == null) {
                    exemplo = programa + "\nesperado: " + esperado + "\nobtido:   " + saida;
                }
            }
            checar("150 programas gerados com um defeito " + defeito + ": exatamente esse erro, na linha certa"
                    + (exemplo == null ? "" : " (ex.:\n" + exemplo + ")"), certos == tentativas);
        }
    }

    // --------------------------------------------------------- textos aleatórios

    private static void verificarAleatorios() {
        Random rnd = new Random(77);
        TabelaSLR tabela = AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA);
        AnalisadorSintatico semAcoes = new AnalisadorSintatico(tabela);
        int programas = 2500;
        int excecoes = 0;
        int sintaticosDiferentes = 0;
        int semanticoDepois = 0;
        int naoDeterministico = 0;
        String exemplo = null;
        String[] pedacos = {"program", "p", ";", "begin", "end", "var", "x", ":", "integer", "real", "char", "string",
                ",", "procedure", "q", "(", ")", ":=", "+", "-", "*", "div", "mod", "<", "=", "if", "then", "else",
                "while", "do", "1", "2.5", "'a'", "\"s\"", "read", "write", "writeln", ".", "@", "\n"};
        for (int i = 0; i < programas; i++) {
            String texto;
            if (i % 2 == 0) {
                // Programa gerado bem tipado, com algumas palavras trocadas, apagadas ou inseridas.
                String base = new GeradorDeProgramas(rnd).gerar(null);
                List<String> partes = new ArrayList<>(Arrays.asList(base.split(" ")));
                int mudancas = 1 + rnd.nextInt(3);
                for (int m = 0; m < mudancas && !partes.isEmpty(); m++) {
                    int k = rnd.nextInt(partes.size());
                    switch (rnd.nextInt(3)) {
                        case 0:
                            partes.remove(k);
                            break;
                        case 1:
                            partes.set(k, pedacos[rnd.nextInt(pedacos.length)]);
                            break;
                        default:
                            partes.add(k, pedacos[rnd.nextInt(pedacos.length)]);
                    }
                }
                texto = String.join(" ", partes);
            } else {
                StringBuilder sb = new StringBuilder("program p; ");
                int n = rnd.nextInt(50);
                for (int k = 0; k < n; k++) {
                    sb.append(pedacos[rnd.nextInt(pedacos.length)]).append(' ');
                }
                texto = sb.toString();
            }
            try {
                ResultadoCompilacao r = COMPILADOR.analisar(texto);
                ResultadoAnalise s = semAcoes.analisar(texto);
                List<String> naoSemanticos = new ArrayList<>();
                int primeiraSintatica = Integer.MAX_VALUE;
                for (ErroCompilacao e : r.getErros()) {
                    if (e.getCategoria() != CategoriaErro.SEMANTICO) {
                        naoSemanticos.add(e.toString());
                    }
                    if (e.getCategoria() == CategoriaErro.SINTATICO) {
                        primeiraSintatica = Math.min(primeiraSintatica, e.getPosicao());
                    }
                }
                List<String> daSintatica = new ArrayList<>();
                for (ErroCompilacao e : s.getErros()) {
                    daSintatica.add(e.toString());
                }
                if (!naoSemanticos.equals(daSintatica)) {
                    sintaticosDiferentes++;
                    exemplo = texto;
                }
                for (ErroCompilacao e : r.errosDa(CategoriaErro.SEMANTICO)) {
                    if (e.getPosicao() >= primeiraSintatica) {
                        semanticoDepois++;
                        exemplo = texto;
                    }
                }
                if (!r.formatar().equals(COMPILADOR.analisar(texto).formatar())) {
                    naoDeterministico++;
                }
            } catch (RuntimeException | StackOverflowError e) {
                excecoes++;
                exemplo = texto + " -> " + e;
            }
        }
        String ex = exemplo == null ? "" : " (ex.: " + exemplo + ")";
        checar(programas + " textos aleatórios e programas mutilados: nenhuma exceção" + (excecoes == 0 ? "" : ex),
                excecoes == 0);
        checar("as ações semânticas não mudam os erros léxicos e sintáticos"
                + (sintaticosDiferentes == 0 ? "" : ex), sintaticosDiferentes == 0);
        checar("nenhum erro semântico depois do primeiro erro sintático" + (semanticoDepois == 0 ? "" : ex),
                semanticoDepois == 0);
        checar("a mesma entrada sempre dá a mesma saída", naoDeterministico == 0);

        TabelaPalavrasReservadas palavras = new TabelaPalavrasReservadas();
        Compilador outro = new Compilador(tabela, palavras);
        String programa = "program p; var x: integer; begin x := 'a'; end.";
        checar("análises seguidas não se misturam (cada uma tem sua tabela de símbolos)",
                outro.analisar(programa).formatar().equals(outro.analisar(programa).formatar())
                        && outro.analisar(programa).getTabelaSimbolos() != outro.analisar(programa).getTabelaSimbolos());
    }

    // ------------------------------------------------------------------ apoio

    // O texto de gramatica.txt, como está no classpath (para montar variações dele).
    private static String textoDaGramaticaCompleta() {
        try (java.io.InputStream in = LeitorGramatica.class.getResourceAsStream(AnalisadorSintatico.GRAMATICA_COMPLETA)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, lidos);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String linhas(String... linhas) {
        return String.join("\n", linhas);
    }

    private static void checarSaida(String descricao, String fonte, String... esperado) {
        String obtido = COMPILADOR.analisar(fonte).formatar();
        String esperadoTexto = String.join("\n", esperado);
        boolean ok = obtido.equals(esperadoTexto);
        checar(descricao + (ok ? "" : " (obtido:\n" + obtido + "\n)"), ok);
    }

    private static void executar(String parte, Runnable verificacoes) {
        try {
            verificacoes.run();
        } catch (RuntimeException e) {
            checar(parte + ": exceção inesperada " + e, false);
        }
    }

    private static void terminar() {
        System.out.println();
        System.out.println(total + " verificações da análise semântica, " + falhas + " falha(s).");
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
