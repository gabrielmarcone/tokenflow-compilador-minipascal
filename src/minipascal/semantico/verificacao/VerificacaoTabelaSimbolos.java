package minipascal.semantico.verificacao;

import minipascal.cli.Console;
import minipascal.semantico.CategoriaSimbolo;
import minipascal.semantico.Simbolo;
import minipascal.semantico.TabelaSimbolos;
import minipascal.semantico.Tipo;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifica a tabela de símbolos (escopos aninhados) e as regras de tipos
 * usadas pela análise semântica, isoladas do analisador:
 *   - escopos: externo (nível 0), global (1), procedimentos (2, 3...), com
 *     nomes "q" e "q.r";
 *   - declarar recusa nome repetido no mesmo escopo e aceita em escopos
 *     diferentes; buscar acha o mais interno; fechar o escopo esconde os locais;
 *   - nomes sem diferença de maiúsculas e minúsculas;
 *   - histórico (inclusive de escopos fechados) e a listagem em texto;
 *   - Tipo: tipos declarados e a tabela completa de atribuição.
 */
public class VerificacaoTabelaSimbolos {

    private static int total = 0;
    private static int falhas = 0;

    public static void main(String[] args) {
        executar("escopos", VerificacaoTabelaSimbolos::verificarEscopos);
        executar("declaração e busca", VerificacaoTabelaSimbolos::verificarDeclaracao);
        executar("histórico e listagem", VerificacaoTabelaSimbolos::verificarListagem);
        executar("tipos", VerificacaoTabelaSimbolos::verificarTipos);
        terminar();
    }

    private static Simbolo variavel(String nome, Tipo tipo, int linha) {
        return new Simbolo(nome, CategoriaSimbolo.VARIAVEL, tipo, linha);
    }

    // ---------------------------------------------------------------- escopos

    private static void verificarEscopos() {
        TabelaSimbolos t = new TabelaSimbolos();
        checar("começa no escopo externo, nível 0",
                t.nivelAtual() == 0 && t.escopoAtual().equals(TabelaSimbolos.ESCOPO_EXTERNO));
        t.abrirEscopo(TabelaSimbolos.ESCOPO_GLOBAL);
        checar("abre o global no nível 1", t.nivelAtual() == 1 && t.escopoAtual().equals("global"));
        t.abrirEscopo("soma");
        checar("procedimento do programa: escopo 'soma', nível 2",
                t.nivelAtual() == 2 && t.escopoAtual().equals("soma"));
        t.abrirEscopo("aux");
        checar("procedimento dentro de procedimento: escopo 'soma.aux', nível 3",
                t.nivelAtual() == 3 && t.escopoAtual().equals("soma.aux"));
        t.fecharEscopo();
        t.fecharEscopo();
        checar("fechar volta ao escopo de fora", t.nivelAtual() == 1 && t.escopoAtual().equals("global"));
        t.fecharEscopo();
        boolean recusou = false;
        try {
            t.fecharEscopo();
        } catch (IllegalStateException e) {
            recusou = true;
        }
        checar("o escopo externo não pode ser fechado", recusou && t.nivelAtual() == 0);
    }

    // ----------------------------------------------------- declaração e busca

    private static void verificarDeclaracao() {
        TabelaSimbolos t = new TabelaSimbolos();
        t.abrirEscopo(TabelaSimbolos.ESCOPO_GLOBAL);
        Simbolo x = variavel("x", Tipo.INTEGER, 2);
        checar("declarar um nome novo devolve null", t.declarar(x) == null);
        checar("o símbolo recebe escopo e nível", x.getEscopo().equals("global") && x.getNivel() == 1);
        Simbolo outroX = variavel("x", Tipo.REAL, 3);
        checar("nome repetido no mesmo escopo: devolve o que já estava e não insere",
                t.declarar(outroX) == x && t.buscar("x") == x && outroX.getNivel() == -1);
        checar("maiúsculas e minúsculas não diferenciam nomes (X = x)",
                t.declarar(variavel("X", Tipo.CHAR, 4)) == x && t.buscar("X") == x);
        checar("buscar um nome que não existe devolve null", t.buscar("y") == null);

        t.abrirEscopo("q");
        Simbolo xLocal = variavel("x", Tipo.STRING, 6);
        checar("o mesmo nome num escopo interno é aceito", t.declarar(xLocal) == null);
        checar("buscar acha o mais interno (o local esconde o global)", t.buscar("x") == xLocal);
        checar("buscarNoEscopoAtual só olha o escopo atual",
                t.buscarNoEscopoAtual("x") == xLocal && t.buscarNoEscopoAtual("nada") == null);
        Simbolo g = variavel("g", Tipo.INTEGER, 2);
        t.fecharEscopo();
        t.declarar(g);
        t.abrirEscopo("r");
        checar("nomes de escopos de fora continuam visíveis dentro",
                t.buscar("g") == g && t.buscarNoEscopoAtual("g") == null);
        t.fecharEscopo();
        checar("depois de fechar, o local some e o global volta", t.buscar("x") == x);

        TabelaSimbolos acentos = new TabelaSimbolos();
        acentos.abrirEscopo(TabelaSimbolos.ESCOPO_GLOBAL);
        Simbolo v = variavel("Variável", Tipo.INTEGER, 1);
        acentos.declarar(v);
        checar("letras acentuadas também sem diferença de caixa (VARIÁVEL = variável)",
                acentos.buscar("VARIÁVEL") == v && acentos.buscar("variavel") == null);
    }

    // ---------------------------------------------------- histórico e listagem

    private static void verificarListagem() {
        TabelaSimbolos t = new TabelaSimbolos();
        t.declarar(new Simbolo("writeln", CategoriaSimbolo.PROCEDIMENTO_PREDEFINIDO, null, 0));
        t.abrirEscopo(TabelaSimbolos.ESCOPO_GLOBAL);
        t.declarar(variavel("x", Tipo.INTEGER, 2));
        Simbolo q = new Simbolo("q", CategoriaSimbolo.PROCEDIMENTO, null, 3);
        t.declarar(q);
        t.abrirEscopo("q");
        Simbolo r = new Simbolo("r", CategoriaSimbolo.PARAMETRO_VAR, Tipo.REAL, 3);
        Simbolo n = new Simbolo("n", CategoriaSimbolo.PARAMETRO, Tipo.INTEGER, 3);
        t.declarar(r);
        t.declarar(n);
        t.declarar(new Simbolo("zz", CategoriaSimbolo.NAO_DECLARADO, Tipo.ERRO, 5));
        t.fecharEscopo();

        List<String> nomes = new ArrayList<>();
        for (Simbolo s : t.getHistorico()) {
            nomes.add(s.getNome());
        }
        checar("histórico na ordem da declaração, inclusive o escopo já fechado: " + nomes,
                nomes.toString().equals("[writeln, x, q, r, n, zz]"));

        // Os parâmetros são ligados ao procedimento pelo analisador semântico (a
        // descrição com parâmetros é conferida em VerificacaoSemantica).
        Simbolo comParametros = new Simbolo("soma", CategoriaSimbolo.PROCEDIMENTO, null, 1);
        checar("procedimento sem parâmetros: descrição vazia", comParametros.descreverParametros().isEmpty());

        String esperado = String.join("\n",
                "Escopo   Nível  Nome     Categoria                 Tipo     Linha  Parâmetros",
                "externo  0      writeln  procedimento predefinido  -        -",
                "global   1      x        variável                  integer  2",
                "global   1      q        procedimento              -        3",
                "q        2      r        parâmetro var             real     3",
                "q        2      n        parâmetro                 integer  3") + "\n";
        String obtido = t.listar();
        checar("listagem alinhada, sem os nomes não declarados" + (obtido.equals(esperado) ? "" : ":\n" + obtido),
                obtido.equals(esperado));
    }

    // ----------------------------------------------------------------- tipos

    private static void verificarTipos() {
        checar("tipos declarados em qualquer caixa",
                Tipo.declarado("integer") == Tipo.INTEGER && Tipo.declarado("REAL") == Tipo.REAL
                        && Tipo.declarado("Char") == Tipo.CHAR && Tipo.declarado("sTrInG") == Tipo.STRING);
        boolean recusou = false;
        try {
            Tipo.declarado("boolean");
        } catch (IllegalArgumentException e) {
            recusou = true;
        }
        checar("'boolean' não é tipo da linguagem", recusou);
        checar("numéricos: integer e real", Tipo.INTEGER.isNumerico() && Tipo.REAL.isNumerico()
                && !Tipo.CHAR.isNumerico() && !Tipo.LOGICO.isNumerico() && !Tipo.ERRO.isNumerico());
        checar("textos: char e string", Tipo.CHAR.isTexto() && Tipo.STRING.isTexto()
                && !Tipo.INTEGER.isTexto() && !Tipo.LOGICO.isTexto());

        // Tabela completa: linha = tipo do valor, coluna = tipo da variável.
        Tipo[] tipos = {Tipo.INTEGER, Tipo.REAL, Tipo.CHAR, Tipo.STRING, Tipo.LOGICO};
        String[] permitido = {
                // integer real  char  string lógico   (destino)
                "s s n n n",  // integer
                "n s n n n",  // real
                "n n s s n",  // char
                "n n n s n",  // string
                "n n n n s"}; // lógico
        List<String> errados = new ArrayList<>();
        for (int i = 0; i < tipos.length; i++) {
            String[] linha = permitido[i].split(" ");
            for (int j = 0; j < tipos.length; j++) {
                boolean esperado = linha[j].equals("s");
                if (tipos[i].podeSerAtribuidoA(tipos[j]) != esperado) {
                    errados.add(tipos[i] + " -> " + tipos[j]);
                }
            }
        }
        checar("atribuição: iguais, integer em real e char em string; nada mais" + (errados.isEmpty() ? "" : " " + errados),
                errados.isEmpty());
        checar("nomes nas mensagens", Tipo.INTEGER.toString().equals("integer") && Tipo.LOGICO.toString().equals("lógico"));
    }

    // ------------------------------------------------------------------ apoio

    private static void executar(String parte, Runnable verificacoes) {
        try {
            verificacoes.run();
        } catch (RuntimeException e) {
            checar(parte + ": exceção inesperada " + e, false);
        }
    }

    private static void terminar() {
        System.out.println();
        System.out.println(total + " verificacoes da tabela de simbolos, " + falhas + " falha(s).");
        System.exit(falhas > 0 ? 1 : 0);
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
