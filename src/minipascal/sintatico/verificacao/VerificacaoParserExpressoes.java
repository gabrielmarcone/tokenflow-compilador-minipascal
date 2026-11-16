package minipascal.sintatico.verificacao;

import minipascal.cli.Console;
import minipascal.erros.CategoriaErro;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.parser.AcoesReducao;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.parser.Terminal;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Verifica o analisador sintático SLR(1) sobre o subconjunto das expressões,
 * sempre a partir de texto-fonte de verdade (passando pelo léxico da Unidade I
 * e pelo adaptador): aceitação, estrutura das reduções (precedência e
 * associatividade), mensagens de erro com tipo e linha e convivência com erros
 * léxicos. A linha de comando, que usa a gramática completa, é verificada em
 * VerificacaoParserCompleto.
 */
public class VerificacaoParserExpressoes {

    private static int total = 0;
    private static int falhas = 0;

    private static TabelaSLR TABELA;
    private static AnalisadorSintatico RECONHECEDOR;
    private static AnalisadorSintatico ARVORE;

    // Começo das mensagens mais comuns (os "esperados" saem da tabela, em ordem da gramática).
    private static final String INICIO_DE_EXPRESSAO =
            "'+', '-', número, identificador, '(', constante string ou constante char";
    private static final String INICIO_DE_TERMO =
            "número, identificador, '(', constante string ou constante char";
    private static final String DEPOIS_DE_OPERANDO =
            "'=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div', 'mod'";

    public static void main(String[] args) {
        // Se a gramática tiver conflito, a tabela não é construída: isso vira uma
        // falha relatada (e não uma exceção solta) e as demais partes não rodam.
        try {
            TABELA = AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_EXPRESSOES);
            RECONHECEDOR = new AnalisadorSintatico(TABELA);
            ARVORE = new AnalisadorSintatico(TABELA, new TabelaPalavrasReservadas(), new AcoesArvore());
        } catch (RuntimeException e) {
            checar("expressoes.txt gera a tabela SLR(1): " + e.getMessage(), false);
            System.out.println();
            System.out.println(total + " verificacoes do parser de expressoes, " + falhas + " falha(s).");
            System.exit(1);
        }
        executar("expressões válidas", VerificacaoParserExpressoes::verificarValidas);
        executar("estrutura das reduções", VerificacaoParserExpressoes::verificarEstrutura);
        executar("erros sintáticos", VerificacaoParserExpressoes::verificarErrosSintaticos);
        executar("erros léxicos", VerificacaoParserExpressoes::verificarErrosLexicos);
        executar("expressões geradas da gramática", VerificacaoParserExpressoes::verificarGeradas);

        System.out.println();
        System.out.println(total + " verificacoes do parser de expressoes, " + falhas + " falha(s).");
        if (falhas > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- válidas

    private static void verificarValidas() {
        String[] validas = {
                "a",
                "10",
                "3.14",
                "24.40e-04",
                "\"texto\"",
                "'c'",
                "a + 2 * (b - 1)",
                "-x mod 3 <= \"abc\"",
                "'c' <> ch",
                "+ y",
                "((((a))))",
                "A MOD B DIV c",
                "total * 1.5E+3 >= limite",
                "a /* comentário no meio */ + b",
                "a\n+\nb\n*\nc",
                "(a + b) * (c - d) div 2 = 0",
                "x <> y",
                "x = y",
                "a<b",
                "a+b*c-d"};
        for (String v : validas) {
            ResultadoAnalise r = RECONHECEDOR.analisar(v);
            checar("válida: " + visivel(v) + (r.isSucesso() ? "" : " -> " + r.formatar()),
                    r.isSucesso() && r.formatar().equals("OK") && r.getErros().isEmpty());
        }
        checar("sem ações, o valor final é null", RECONHECEDOR.analisar("a + b").getValor() == null);
        checar("a tabela embutida é montada uma única vez (mesma instância)",
                AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_EXPRESSOES) == TABELA);
    }

    // ---------------------------------------------------- estrutura (árvore)

    private static void verificarEstrutura() {
        checarArvore("a + b * c", "(a + (b * c))");
        checarArvore("a * b + c", "((a * b) + c)");
        checarArvore("a - b - c", "((a - b) - c)");
        checarArvore("a * b div c mod d", "(((a * b) div c) mod d)");
        checarArvore("(a + b) * c", "((a + b) * c)");
        checarArvore("-a * b", "(-(a * b))");
        checarArvore("-a + b", "((-a) + b)");
        checarArvore("a < b + 1", "(a < (b + 1))");
        checarArvore("a = (b <> c)", "(a = (b <> c))");
        checarArvore("x + 2 * (y - 3) >= \"s\"", "((x + (2 * (y - 3))) >= \"s\")");

        // Linha de uma construção = linha do primeiro token dela.
        AcoesLinhas linhas = new AcoesLinhas();
        new AnalisadorSintatico(TABELA, new TabelaPalavrasReservadas(), linhas).analisar("\n\na\n<\n(b\n+ c)");
        checar("linha da redução EXPR -> SIEXPR OPREL SIEXPR é a do 'a' (3)",
                linhas.linhaDe("EXPR -> SIEXPR OPREL SIEXPR") == 3);
        checar("linha da redução FATOR -> ( EXPR ) é a do '(' (5)",
                linhas.linhaDe("FATOR -> ( EXPR )") == 5);
        checar("linha da redução SIEXPR -> SIEXPR + TERMO é a do 'b' (5)",
                linhas.linhaDe("SIEXPR -> SIEXPR + TERMO") == 5);
        checar("com erro sintático, o valor final fica null", ARVORE.analisar("a +").getValor() == null);
    }

    // ------------------------------------------------------ erros sintáticos

    private static void verificarErrosSintaticos() {
        checarErro("a b", "Erro sintático na linha 1: Encontrado identificador 'b'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("(a b", "Erro sintático na linha 1: Encontrado identificador 'b'; esperado "
                + DEPOIS_DE_OPERANDO + " ou ')'");
        checarErro("a +", "Erro sintático na linha 1: Fim do arquivo inesperado; esperado " + INICIO_DE_TERMO);
        checarErro("a <", "Erro sintático na linha 1: Fim do arquivo inesperado; esperado " + INICIO_DE_EXPRESSAO);
        checarErro("a < b < c", "Erro sintático na linha 1: Encontrado '<'; esperado "
                + "'+', '-', '*', 'div', 'mod' ou fim do arquivo");
        checarErro("a / b", "Erro sintático na linha 1: Encontrado '/'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("a and b", "Erro sintático na linha 1: Encontrado 'and'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("", "Erro sintático na linha 1: Fim do arquivo inesperado; esperado " + INICIO_DE_EXPRESSAO);
        checarErro("( )", "Erro sintático na linha 1: Encontrado ')'; esperado " + INICIO_DE_EXPRESSAO);
        checarErro("a )", "Erro sintático na linha 1: Encontrado ')'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("a * - b", "Erro sintático na linha 1: Encontrado '-'; esperado " + INICIO_DE_TERMO);
        checarErro("+ - a", "Erro sintático na linha 1: Encontrado '-'; esperado " + INICIO_DE_TERMO);
        checarErro("not a", "Erro sintático na linha 1: Encontrado 'not'; esperado " + INICIO_DE_EXPRESSAO);
        checarErro("a := 1", "Erro sintático na linha 1: Encontrado ':='; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("a ; b", "Erro sintático na linha 1: Encontrado ';'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("begin", "Erro sintático na linha 1: Encontrado 'begin'; esperado " + INICIO_DE_EXPRESSAO);
        checarErro("integer", "Erro sintático na linha 1: Encontrado tipo 'integer'; esperado " + INICIO_DE_EXPRESSAO);
        checarErro("1 2.5", "Erro sintático na linha 1: Encontrado número '2.5'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("a \"s\"", "Erro sintático na linha 1: Encontrado constante string \"s\"; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo");
        checarErro("a +\n\n* b", "Erro sintático na linha 3: Encontrado '*'; esperado " + INICIO_DE_TERMO);
        checarErro("(a\n+ b\n\n", "Erro sintático na linha 2: Fim do arquivo inesperado; esperado "
                + "'=', '<', '>', '<>', '<=', '>=', '+', '-', '*', 'div', 'mod' ou ')'");

        ResultadoAnalise r = RECONHECEDOR.analisar("a b c d");
        checar("erros colados contam como um só (um só erro em 'a b c d'; ver VerificacaoRecuperacao)",
                r.getErros().size() == 1 && r.errosDa(CategoriaErro.SINTATICO).size() == 1);
        checar("o léxico lê o texto todo (4 tokens)", r.getTokens().size() == 4);
    }

    // --------------------------------------------------------- erros léxicos

    private static void verificarErrosLexicos() {
        checarSaida("a @ + b", "Erro léxico na linha 1: Caractere inválido: @");
        checarSaida("a @ +", "Erro léxico na linha 1: Caractere inválido: @\n"
                + "Erro sintático na linha 1: Fim do arquivo inesperado; esperado " + INICIO_DE_TERMO);
        checarSaida("a b @", "Erro sintático na linha 1: Encontrado identificador 'b'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo\n"
                + "Erro léxico na linha 1: Caractere inválido: @");
        checarSaida("a b\nc @", "Erro sintático na linha 1: Encontrado identificador 'b'; esperado "
                + DEPOIS_DE_OPERANDO + " ou fim do arquivo\n"
                + "Erro léxico na linha 2: Caractere inválido: @");
        // o fim do arquivo fica na linha do último token lido (a string aberta, linha 2)
        checarSaida("x +\n\"abc", "Erro léxico na linha 2: String não fechada (falta \"): \"abc\n"
                + "Erro sintático na linha 2: Fim do arquivo inesperado; esperado " + INICIO_DE_TERMO);
        checarSaida("/* x", "Erro léxico na linha 1: Comentário não fechado (falta */): /*\n"
                + "Erro sintático na linha 1: Fim do arquivo inesperado; esperado " + INICIO_DE_EXPRESSAO);
        // 'a (erro), b, ' seguido de espaço (erro), + e 1: sem os erros, "b + 1" é válida
        checarSaida("'ab' + 1", "Erro léxico na linha 1: Char deve ter 1 caractere: 'a\n"
                + "Erro léxico na linha 1: Char deve ter 1 caractere: ' ");

        ResultadoAnalise r = RECONHECEDOR.analisar("a @ + b");
        checar("só erro léxico: não é sucesso, mas não há erro sintático",
                !r.isSucesso() && r.errosDa(CategoriaErro.SINTATICO).isEmpty()
                        && r.errosDa(CategoriaErro.LEXICO).size() == 1);
        checar("tokens incluem o de erro léxico (4)", r.getTokens().size() == 4);
    }

    // --------------------------------------------- expressões geradas (aleatórias)

    private static void verificarGeradas() {
        Gramatica g = TABELA.getGramatica();
        Random rnd = new Random(2026);
        int aceitas = 0;
        String primeiraRecusada = null;
        for (int i = 0; i < 2000; i++) {
            String fonte = gerarFonte(g, "EXPR", 0, rnd);
            ResultadoAnalise r = RECONHECEDOR.analisar(fonte);
            if (r.isSucesso()) {
                aceitas++;
            } else if (primeiraRecusada == null) {
                primeiraRecusada = fonte + " -> " + r.formatar();
            }
        }
        checar("2000 expressões geradas a partir da gramática, todas aceitas"
                + (primeiraRecusada == null ? "" : " (recusada: " + primeiraRecusada + ")"), aceitas == 2000);
    }

    // Deriva uma expressão aleatória e escreve como texto-fonte (lexemas reais).
    private static String gerarFonte(Gramatica g, String simbolo, int profundidade, Random rnd) {
        if (g.ehTerminal(simbolo)) {
            return lexemaDe(simbolo, rnd);
        }
        List<Producao> opcoes = g.producoesDe(simbolo);
        Producao escolhida = opcoes.get(rnd.nextInt(opcoes.size()));
        if (profundidade > 6) {
            // perto do limite, prefere a produção com menos não terminais
            for (Producao p : opcoes) {
                if (contarNaoTerminais(g, p) < contarNaoTerminais(g, escolhida)) {
                    escolhida = p;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : escolhida.getCorpo()) {
            sb.append(gerarFonte(g, s, profundidade + 1, rnd)).append(rnd.nextBoolean() ? " " : "\n");
        }
        return sb.toString();
    }

    private static int contarNaoTerminais(Gramatica g, Producao p) {
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
                return new String[]{"x", "total", "Media_2", "a1", "LIMITE"}[rnd.nextInt(5)];
            case Terminal.NUMERO:
                return new String[]{"0", "42", "3.14", "1.5e-3", "24.40E+10"}[rnd.nextInt(5)];
            case Terminal.CONSTANTE_STRING:
                return "\"texto\"";
            case Terminal.CONSTANTE_CHAR:
                return "'c'";
            case "div":
                return rnd.nextBoolean() ? "div" : "DIV";
            case "mod":
                return rnd.nextBoolean() ? "mod" : "Mod";
            default:
                return terminal;
        }
    }

    // ------------------------------------------------------------------ apoio

    // Monta a expressão totalmente parentesada a partir das reduções.
    private static final class AcoesArvore implements AcoesReducao {
        @Override
        public Object aoEmpilhar(Terminal terminal) {
            return terminal.getLexema();
        }

        @Override
        public Object aoReduzir(Producao p, List<Object> v, int linha) {
            if (v.size() == 1) {
                return v.get(0);
            }
            if (v.size() == 2) {
                return "(" + v.get(0) + v.get(1) + ")";
            }
            if ("(".equals(v.get(0))) {
                return v.get(1);
            }
            return "(" + v.get(0) + " " + v.get(1) + " " + v.get(2) + ")";
        }
    }

    // Guarda a linha da primeira redução de cada produção.
    private static final class AcoesLinhas implements AcoesReducao {
        private final List<String> producoes = new ArrayList<>();
        private final List<Integer> linhas = new ArrayList<>();

        @Override
        public Object aoReduzir(Producao p, List<Object> v, int linha) {
            producoes.add(p.toString());
            linhas.add(linha);
            return null;
        }

        int linhaDe(String producao) {
            int i = producoes.indexOf(producao);
            return i < 0 ? -1 : linhas.get(i);
        }
    }

    private static void checarArvore(String fonte, String esperado) {
        ResultadoAnalise r = ARVORE.analisar(fonte);
        boolean ok = r.isSucesso() && esperado.equals(r.getValor());
        checar("estrutura de " + fonte + " = " + esperado + (ok ? "" : " (obtido: " + r.getValor() + ")"), ok);
    }

    private static void checarErro(String fonte, String esperado) {
        ResultadoAnalise r = RECONHECEDOR.analisar(fonte);
        boolean ok = !r.isSucesso() && r.getErros().size() == 1
                && r.getErros().get(0).getCategoria() == CategoriaErro.SINTATICO
                && r.formatar().equals(esperado);
        checar("erro em " + visivel(fonte) + (ok ? "" : " (obtido: " + r.formatar() + ")"), ok);
    }

    private static void checarSaida(String fonte, String esperado) {
        String obtido = RECONHECEDOR.analisar(fonte).formatar();
        boolean ok = obtido.equals(esperado);
        checar("saída de " + visivel(fonte) + (ok ? "" : " (obtido: " + obtido + ")"), ok);
    }

    private static String visivel(String fonte) {
        return fonte.isEmpty() ? "(vazio)" : "[" + fonte.replace("\n", "\\n") + "]";
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
