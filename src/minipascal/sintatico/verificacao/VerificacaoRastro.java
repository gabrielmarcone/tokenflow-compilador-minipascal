package minipascal.sintatico.verificacao;

import minipascal.lexico.model.TipoToken;
import minipascal.lexico.model.Token;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.LeitorGramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.parser.AcoesReducao;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.PassoAnalise;
import minipascal.sintatico.parser.RastroAnalise;
import minipascal.sintatico.parser.ResultadoAnalise;
import minipascal.sintatico.parser.Terminal;
import minipascal.sintatico.slr.ColecaoLR0;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Verifica o rastro da análise (RastroAnalise): os movimentos da pilha que a
 * interface mostra.
 *
 *   - a tabela de movimentos do livro do Dragão (figura 4.38, entrada
 *     id * id + id com a gramática 4.1), passo a passo;
 *   - reexecução: cada passo leva ao seguinte exatamente como a ação diz
 *     (empilhar acrescenta o estado; reduzir tira |corpo| estados e põe o
 *     GOTO; recuperação só corta a pilha e, no modo pânico, põe um estado);
 *   - os terminais empilhados são os tokens do texto, na ordem;
 *   - os símbolos de cada passo são os símbolos de acesso dos estados;
 *   - erros e recuperações aparecem no rastro, um passo de recuperação logo
 *     depois de cada erro;
 *   - com ou sem rastro, o resultado da análise é o mesmo;
 *   - a entrada restante e o limite de passos.
 */
public class VerificacaoRastro {

    private static int total = 0;
    private static int falhas = 0;

    private static TabelaSLR TABELA;

    public static void main(String[] args) {
        try {
            TABELA = AnalisadorSintatico.tabelaEmbutida(AnalisadorSintatico.GRAMATICA_COMPLETA);
        } catch (RuntimeException e) {
            checar("gramatica.txt gera a tabela SLR(1): " + e.getMessage(), false);
            terminar();
        }
        executar("livro do Dragão", VerificacaoRastro::verificarDragao);
        executar("símbolos de acesso", VerificacaoRastro::verificarSimbolosDeAcesso);
        executar("erros e recuperação", VerificacaoRastro::verificarRecuperacao);
        executar("entrada restante", VerificacaoRastro::verificarEntradaRestante);
        executar("reexecução", VerificacaoRastro::verificarReexecucao);
        executar("limite", VerificacaoRastro::verificarLimite);
        terminar();
    }

    // ------------------------------------------------------- livro do Dragão

    private static void verificarDragao() {
        TabelaSLR t = TabelaSLR.construir(LeitorGramatica.lerTexto(
                "E -> E + T | T\nT -> T * F | F\nF -> ( E ) | id\n"));
        AnalisadorSintatico a = new AnalisadorSintatico(t, new TabelaPalavrasReservadas(), AcoesReducao.NENHUMA);
        RastroAnalise rastro = new RastroAnalise(t);
        ResultadoAnalise r = a.analisar("id * id + id", rastro);
        // Figura 4.38 do livro: pilha | símbolos | entrada | ação.
        String[] esperado = {
                "0 |  | id * id + id $ | s5",
                "0 5 | id | * id + id $ | r6",
                "0 3 | F | * id + id $ | r4",
                "0 2 | T | * id + id $ | s7",
                "0 2 7 | T * | id + id $ | s5",
                "0 2 7 5 | T * id | + id $ | r6",
                "0 2 7 10 | T * F | + id $ | r3",
                "0 2 | T | + id $ | r2",
                "0 1 | E | + id $ | s6",
                "0 1 6 | E + | id $ | s5",
                "0 1 6 5 | E + id | $ | r6",
                "0 1 6 3 | E + F | $ | r4",
                "0 1 6 9 | E + T | $ | r1",
                "0 1 | E | $ | acc"};
        List<String> obtido = new ArrayList<>();
        for (PassoAnalise p : rastro.getPassos()) {
            obtido.add(p.pilhaComoTexto() + " | " + p.simbolosComoTexto() + " | "
                    + RastroAnalise.entradaRestante(p, r.getTokens(), 20) + " | " + p.getCodigo());
        }
        boolean igual = obtido.equals(Arrays.asList(esperado));
        checar("id * id + id: os 14 passos da figura 4.38 (pilha, símbolos, entrada e ação)"
                + (igual ? "" : " (obtido: " + obtido + ")"), igual);
        PassoAnalise reducao = rastro.getPassos().get(6);
        checar("descrição da redução: 'reduz pela produção 3: T -> T * F'",
                reducao.getDescricao().equals("reduz pela produção 3: T -> T * F")
                        && reducao.getTipo() == PassoAnalise.Tipo.REDUZIR);
        PassoAnalise empilha = rastro.getPassos().get(0);
        checar("descrição do empilhamento: 'empilha 'id' e vai para o estado 5'",
                empilha.getDescricao().equals("empilha 'id' e vai para o estado 5")
                        && empilha.getTerminal().equals("id") && empilha.getTopo() == 0);
        checar("o último passo é aceitar", rastro.getPassos().get(13).getTipo() == PassoAnalise.Tipo.ACEITAR
                && r.isSucesso() && !rastro.isTruncado());
    }

    // ------------------------------------------------------ símbolos de acesso

    private static void verificarSimbolosDeAcesso() {
        RastroAnalise rastro = new RastroAnalise(TABELA);
        ColecaoLR0 colecao = TABELA.getAnalise().getColecao();
        List<String> divergentes = new ArrayList<>();
        for (int s = 0; s < colecao.tamanho(); s++) {
            for (Map.Entry<String, Integer> tr : colecao.transicoesDe(s).entrySet()) {
                if (!rastro.simboloDeAcesso(tr.getValue()).equals(tr.getKey())) {
                    divergentes.add(s + " --" + tr.getKey() + "--> " + tr.getValue());
                }
            }
        }
        checar("todas as transições que chegam a um estado têm o mesmo símbolo (o de acesso)" + lista(divergentes),
                divergentes.isEmpty());
        boolean todosTem = rastro.simboloDeAcesso(0).isEmpty();
        for (int s = 1; s < colecao.tamanho(); s++) {
            todosTem &= !rastro.simboloDeAcesso(s).isEmpty();
        }
        checar("o estado 0 não tem símbolo; os outros " + (colecao.tamanho() - 1) + " têm", todosTem);
    }

    // ---------------------------------------------------- erros e recuperação

    private static void verificarRecuperacao() {
        AnalisadorSintatico a = new AnalisadorSintatico(TABELA);
        RastroAnalise rastro = new RastroAnalise(TABELA);
        String fonte = "program p begin if x > 1 x := 2 else x := 3; for i := 1 to 3 do x := 1; end.";
        ResultadoAnalise r = a.analisar(fonte, rastro);
        List<String> recuperacoes = new ArrayList<>();
        int erros = 0;
        boolean recuperacaoLogoDepois = true;
        List<PassoAnalise> passos = rastro.getPassos();
        for (int i = 0; i < passos.size(); i++) {
            if (passos.get(i).getTipo() == PassoAnalise.Tipo.ERRO) {
                erros++;
                recuperacaoLogoDepois &= i + 1 < passos.size()
                        && passos.get(i + 1).getTipo() == PassoAnalise.Tipo.RECUPERACAO;
            }
            if (passos.get(i).getTipo() == PassoAnalise.Tipo.RECUPERACAO) {
                recuperacoes.add(passos.get(i).getDescricao());
            }
        }
        checar("um passo de erro para cada erro sintático (3), cada um seguido da recuperação",
                erros == 3 && erros == r.getErros().size() && recuperacaoLogoDepois);
        boolean descritas = recuperacoes.equals(Arrays.asList(
                "recuperação: insere ';', que faltava",
                "recuperação: insere 'then', que faltava",
                "recuperação: descarta 'for', 'i', ':=', '1', 'to'; insere 'while', que faltava"));
        checar("recuperações descritas: inserção de ';', de 'then' e descarte seguido de inserção"
                + (descritas ? "" : " " + recuperacoes), descritas);
        PassoAnalise erro = passos.get(2);
        checar("o passo de erro traz a mensagem do erro",
                erro.getDescricao().equals("erro: Encontrado 'begin'; esperado ';'") && erro.getLinha() == 1);
        PassoAnalise inserido = passos.get(4);
        checar("o terminal inserido é empilhado e aparece marcado na entrada",
                inserido.isInserido() && inserido.getDescricao().equals("empilha ';' (inserido) e vai para o estado 4")
                        && RastroAnalise.entradaRestante(inserido, r.getTokens(), 3).equals("«;» begin if …"));

        RastroAnalise panico = new RastroAnalise(TABELA);
        a.analisar("program p; begin x := 1 + ; y := 2; z := 3; end.", panico);
        String descricao = "";
        for (PassoAnalise p : panico.getPassos()) {
            if (p.getTipo() == PassoAnalise.Tipo.RECUPERACAO) {
                descricao = p.getDescricao();
            }
        }
        checar("modo pânico descrito: estados desempilhados e o não terminal considerado reconhecido (" + descricao + ")",
                descricao.matches("recuperação: (desempilha \\d+ estados?, )?considera [A-Z_]+ reconhecido "
                        + "e vai para o estado \\d+"));
    }

    // ------------------------------------------------------- entrada restante

    private static void verificarEntradaRestante() {
        AnalisadorSintatico a = new AnalisadorSintatico(TABELA);
        RastroAnalise rastro = new RastroAnalise(TABELA);
        ResultadoAnalise r = a.analisar("program p; begin x := 1 @ ; end.", rastro);
        PassoAnalise primeiro = rastro.getPassos().get(0);
        checar("no máximo N lexemas e '…' quando há mais",
                RastroAnalise.entradaRestante(primeiro, r.getTokens(), 4).equals("program p ; begin …"));
        checar("com espaço para tudo: termina em '$', sem os erros léxicos (o '@')",
                RastroAnalise.entradaRestante(primeiro, r.getTokens(), 50).equals("program p ; begin x := 1 ; end . $"));
        PassoAnalise ultimo = rastro.getPassos().get(rastro.getPassos().size() - 1);
        checar("no fim do arquivo: só '$'", RastroAnalise.entradaRestante(ultimo, r.getTokens(), 4).equals("$")
                && ultimo.getLexema().equals("fim do arquivo") && ultimo.getTerminal().equals("$"));
    }

    // ------------------------------------------------------------ reexecução

    private static void verificarReexecucao() {
        Gramatica g = TABELA.getGramatica();
        AnalisadorSintatico a = new AnalisadorSintatico(TABELA);
        Random rnd = new Random(41);
        String[] pedacos = {"program", "p", ";", "begin", "end", "var", "x", ":", "integer", ",", "procedure",
                "(", ")", ":=", "+", "-", "*", "div", "<", "=", "if", "then", "else", "while", "do", "1", "'a'",
                "\"s\"", ".", "@", "\n"};
        int programas = 1500;
        int incoerentes = 0;
        int resultadosDiferentes = 0;
        int empilhadosErrados = 0;
        String exemplo = null;
        for (int i = 0; i < programas; i++) {
            String fonte;
            if (i % 3 == 0) {
                fonte = gerarFonte(g, "PROG", 0, rnd);
            } else {
                List<String> partes = new ArrayList<>(Arrays.asList(gerarFonte(g, "PROG", 0, rnd).split(" ")));
                for (int m = 0; m < 1 + rnd.nextInt(3) && !partes.isEmpty(); m++) {
                    int k = rnd.nextInt(partes.size());
                    if (rnd.nextBoolean()) {
                        partes.remove(k);
                    } else {
                        partes.add(k, pedacos[rnd.nextInt(pedacos.length)]);
                    }
                }
                fonte = String.join(" ", partes);
            }
            RastroAnalise rastro = new RastroAnalise(TABELA);
            ResultadoAnalise com = a.analisar(fonte, rastro);
            ResultadoAnalise sem = a.analisar(fonte);
            if (!com.formatar().equals(sem.formatar()) || com.getTokens().size() != sem.getTokens().size()) {
                resultadosDiferentes++;
                exemplo = fonte;
            }
            String problema = reexecutar(rastro, g);
            if (problema != null) {
                incoerentes++;
                exemplo = fonte + " -> " + problema;
            }
            if (com.isSucesso() && !empilhadosSaoOsTokens(rastro, com.getTokens())) {
                empilhadosErrados++;
                exemplo = fonte;
            }
        }
        String ex = exemplo == null ? "" : " (ex.: " + exemplo + ")";
        checar(programas + " programas (gerados e mutilados): cada passo leva ao seguinte como a ação diz"
                + (incoerentes == 0 ? "" : ex), incoerentes == 0);
        checar("com ou sem rastro, o resultado é o mesmo" + (resultadosDiferentes == 0 ? "" : ex),
                resultadosDiferentes == 0);
        checar("programas corretos: os terminais empilhados são os tokens do texto, na ordem"
                + (empilhadosErrados == 0 ? "" : ex), empilhadosErrados == 0);
    }

    /*
     * Confere o rastro inteiro: pilha e símbolos de cada passo coerentes, e
     * cada passo levando ao seguinte como a ação diz. Devolve o problema, ou null.
     */
    private static String reexecutar(RastroAnalise rastro, Gramatica g) {
        List<PassoAnalise> passos = rastro.getPassos();
        if (passos.isEmpty() || passos.get(passos.size() - 1).getTipo() != PassoAnalise.Tipo.ACEITAR) {
            return "o rastro não termina em aceitar";
        }
        for (int i = 0; i < passos.size(); i++) {
            PassoAnalise p = passos.get(i);
            int[] pilha = p.getEstados();
            String[] simbolos = p.getSimbolos();
            if (pilha[0] != 0 || pilha.length != simbolos.length) {
                return "passo " + p.getNumero() + ": pilha sem o estado 0 na base";
            }
            for (int k = 0; k < pilha.length; k++) {
                if (!simbolos[k].equals(rastro.simboloDeAcesso(pilha[k]))) {
                    return "passo " + p.getNumero() + ": símbolo errado";
                }
            }
            if (p.getNumero() != i + 1) {
                return "numeração";
            }
            if (i + 1 == passos.size()) {
                break;
            }
            int[] seguinte = passos.get(i + 1).getEstados();
            switch (p.getTipo()) {
                case EMPILHAR: {
                    int alvo = Integer.parseInt(p.getCodigo().substring(1));
                    int[] esperado = Arrays.copyOf(pilha, pilha.length + 1);
                    esperado[pilha.length] = alvo;
                    if (!Arrays.equals(esperado, seguinte) || !TABELA.acao(p.getTopo(), p.getTerminal()).toString()
                            .equals(p.getCodigo())) {
                        return "passo " + p.getNumero() + ": empilhar não leva ao passo seguinte";
                    }
                    break;
                }
                case REDUZIR: {
                    Producao prod = g.getProducao(Integer.parseInt(p.getCodigo().substring(1)));
                    int base = pilha.length - prod.tamanho();
                    int[] esperado = Arrays.copyOf(pilha, base + 1);
                    esperado[base] = TABELA.desvio(pilha[base - 1], prod.getCabeca());
                    if (!Arrays.equals(esperado, seguinte)) {
                        return "passo " + p.getNumero() + ": reduzir não leva ao passo seguinte";
                    }
                    break;
                }
                case ERRO:
                    if (passos.get(i + 1).getTipo() != PassoAnalise.Tipo.RECUPERACAO
                            || TABELA.acao(p.getTopo(), p.getTerminal()) != null) {
                        return "passo " + p.getNumero() + ": erro sem recuperação, ou com ação na tabela";
                    }
                    break;
                case RECUPERACAO: {
                    // A pilha seguinte é a mesma (o passo seguinte usa a pilha da recuperação).
                    if (!Arrays.equals(pilha, seguinte)) {
                        return "passo " + p.getNumero() + ": recuperação não leva ao passo seguinte";
                    }
                    break;
                }
                default:
                    return "aceitar antes do fim";
            }
        }
        // Recuperação: a pilha depois dela é a de antes do erro cortada, mais no máximo um estado.
        for (int i = 0; i + 1 < passos.size(); i++) {
            if (passos.get(i).getTipo() == PassoAnalise.Tipo.ERRO) {
                int[] antes = passos.get(i).getEstados();
                int[] depois = passos.get(i + 1).getEstados();
                int comum = 0;
                while (comum < Math.min(antes.length, depois.length) && antes[comum] == depois[comum]) {
                    comum++;
                }
                if (depois.length - comum > 1) {
                    return "passo " + passos.get(i + 1).getNumero() + ": recuperação pôs mais de um estado";
                }
            }
        }
        return null;
    }

    private static boolean empilhadosSaoOsTokens(RastroAnalise rastro, List<Token> tokens) {
        List<String> empilhados = new ArrayList<>();
        for (PassoAnalise p : rastro.getPassos()) {
            if (p.getTipo() == PassoAnalise.Tipo.EMPILHAR) {
                empilhados.add(p.getLexema());
            }
        }
        List<String> doTexto = new ArrayList<>();
        for (Token t : tokens) {
            if (t.getTipo() != TipoToken.ERRO_LEXICO) {
                doTexto.add(t.getLexema());
            }
        }
        return empilhados.equals(doTexto);
    }

    // ------------------------------------------------------------------ limite

    private static void verificarLimite() {
        StringBuilder sb = new StringBuilder("program p; begin ");
        for (int i = 0; i < 6000; i++) {
            sb.append("x := 1 + 2 * 3; ");
        }
        sb.append("end.");
        AnalisadorSintatico a = new AnalisadorSintatico(TABELA);
        RastroAnalise rastro = new RastroAnalise(TABELA);
        ResultadoAnalise r = a.analisar(sb.toString(), rastro);
        checar("programa enorme: o rastro para em 50000 passos e avisa; a análise vai até o fim (OK)",
                rastro.isTruncado() && rastro.getPassos().size() == 50000 && r.isSucesso());
    }

    // ------------------------------------------------------------------ apoio

    private static String gerarFonte(Gramatica g, String simbolo, int profundidade, Random rnd) {
        if (g.ehTerminal(simbolo)) {
            switch (simbolo) {
                case Terminal.ID:
                    return new String[]{"x", "total", "y"}[rnd.nextInt(3)];
                case Terminal.NUMERO:
                    return new String[]{"0", "42", "3.14"}[rnd.nextInt(3)];
                case Terminal.TIPO:
                    return new String[]{"integer", "real", "char", "string"}[rnd.nextInt(4)];
                case Terminal.CONSTANTE_STRING:
                    return "\"texto\"";
                case Terminal.CONSTANTE_CHAR:
                    return "'c'";
                default:
                    return simbolo;
            }
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
            sb.append(gerarFonte(g, s, profundidade + 1, rnd)).append(' ');
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

    private static String lista(List<String> itens) {
        return itens.isEmpty() ? "" : " " + itens;
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
        System.out.println(total + " verificações do rastro da análise, " + falhas + " falha(s).");
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
