package minipascal.sintatico.parser;

import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.TipoToken;
import minipascal.lexico.model.Token;
import minipascal.sintatico.slr.Acao;
import minipascal.sintatico.slr.ColecaoLR0;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Guarda os movimentos da pilha de uma análise, passo a passo (a tabela de
 * movimentos do livro do Dragão): para cada passo, a pilha de estados, os
 * símbolos, o terminal de entrada e a ação.
 *
 * O símbolo de cada estado é o seu símbolo de acesso: no autômato LR(0),
 * todas as transições que chegam a um estado têm o mesmo símbolo (o que está
 * antes do ponto nos seus itens), então a pilha de símbolos sai da pilha de
 * estados.
 *
 * Para não ocupar memória demais com programas enormes, o registro para
 * depois de LIMITE_DE_PASSOS passos ou de LIMITE_DE_CELULAS estados guardados
 * no total (isTruncado avisa); a análise em si continua normalmente.
 */
public final class RastroAnalise implements OuvinteAnalise {

    static final int LIMITE_DE_PASSOS = 50000;
    static final int LIMITE_DE_CELULAS = 5000000;

    private final TabelaSLR tabela;
    private final String[] simboloDeAcesso;
    private final List<PassoAnalise> passos = new ArrayList<>();
    private long celulas = 0;
    private boolean truncado = false;
    // Posição do token do texto que vem depois de um terminal inserido.
    private int posicaoAposInserido = -1;

    public RastroAnalise(TabelaSLR tabela) {
        this.tabela = tabela;
        ColecaoLR0 colecao = tabela.getAnalise().getColecao();
        simboloDeAcesso = new String[colecao.tamanho()];
        simboloDeAcesso[0] = "";
        for (int estado = 0; estado < colecao.tamanho(); estado++) {
            for (Map.Entry<String, Integer> t : colecao.transicoesDe(estado).entrySet()) {
                simboloDeAcesso[t.getValue()] = t.getKey();
            }
        }
    }

    // O símbolo que leva ao estado ("" para o estado 0).
    public String simboloDeAcesso(int estado) {
        return simboloDeAcesso[estado];
    }

    public List<PassoAnalise> getPassos() {
        return Collections.unmodifiableList(passos);
    }

    // Verdadeiro se o registro parou antes do fim da análise (programa muito grande).
    public boolean isTruncado() {
        return truncado;
    }

    @Override
    public void acao(List<Integer> estados, Terminal atual, Acao acao) {
        PassoAnalise.Tipo tipo;
        String descricao;
        if (acao.isEmpilhar()) {
            tipo = PassoAnalise.Tipo.EMPILHAR;
            descricao = "empilha " + descrever(atual) + " e vai para o estado " + acao.getAlvo();
        } else if (acao.isReduzir()) {
            tipo = PassoAnalise.Tipo.REDUZIR;
            descricao = "reduz pela produção " + acao.getAlvo() + ": "
                    + tabela.getGramatica().getProducao(acao.getAlvo());
        } else {
            tipo = PassoAnalise.Tipo.ACEITAR;
            descricao = "aceita: fim da análise";
        }
        registrar(tipo, estados, atual, acao.toString(), descricao);
    }

    @Override
    public void erro(List<Integer> estados, Terminal atual, ErroCompilacao erro) {
        registrar(PassoAnalise.Tipo.ERRO, estados, atual, "", "erro: " + erro.getMensagem());
    }

    @Override
    public void recuperacao(List<Integer> estados, Terminal proximo, int posicaoDoTexto, String descricao) {
        posicaoAposInserido = posicaoDoTexto;
        registrar(PassoAnalise.Tipo.RECUPERACAO, estados, proximo, "", "recuperação: " + descricao);
    }

    private void registrar(PassoAnalise.Tipo tipo, List<Integer> estados, Terminal atual, String codigo,
                           String descricao) {
        if (truncado) {
            return;
        }
        if (passos.size() >= LIMITE_DE_PASSOS || celulas + estados.size() > LIMITE_DE_CELULAS) {
            truncado = true;
            return;
        }
        int[] pilha = new int[estados.size()];
        String[] simbolos = new String[estados.size()];
        for (int i = 0; i < pilha.length; i++) {
            pilha[i] = estados.get(i);
            simbolos[i] = simboloDeAcesso[pilha[i]];
        }
        celulas += pilha.length;
        int posicao = atual.isInserido() ? posicaoAposInserido : atual.getPosicao();
        passos.add(new PassoAnalise(passos.size() + 1, tipo, pilha, simbolos, atual.getNome(), atual.getLexema(),
                atual.isInserido(), posicao, atual.getLinha(), codigo, descricao));
    }

    // "'x' (id)", "';'", "'then' (inserido)", "fim do arquivo ($)"
    private static String descrever(Terminal t) {
        if (t.isFimDeArquivo()) {
            return "fim do arquivo ($)";
        }
        if (t.isInserido()) {
            return "'" + t.getNome() + "' (inserido)";
        }
        return t.getLexema().equals(t.getNome()) ? "'" + t.getLexema() + "'"
                : "'" + t.getLexema() + "' (" + t.getNome() + ")";
    }

    /**
     * A entrada que falta ler num passo, como texto: os lexemas a partir do
     * terminal de entrada (os erros léxicos não entram, porque não chegam ao
     * analisador sintático), no máximo "maximo" deles, e "$" no fim. Um
     * terminal inserido aparece entre « » (caracteres presentes em qualquer fonte).
     *
     * @param tokens os tokens lidos na análise (ResultadoAnalise.getTokens)
     */
    public static String entradaRestante(PassoAnalise passo, List<Token> tokens, int maximo) {
        List<String> partes = new ArrayList<>();
        if (passo.isInserido()) {
            partes.add("«" + passo.getTerminal() + "»");
        }
        int i = Math.max(0, passo.getPosicaoEntrada());
        for (; i < tokens.size() && partes.size() < maximo; i++) {
            if (tokens.get(i).getTipo() != TipoToken.ERRO_LEXICO) {
                partes.add(tokens.get(i).getLexema());
            }
        }
        boolean sobrou = false;
        for (; i < tokens.size(); i++) {
            if (tokens.get(i).getTipo() != TipoToken.ERRO_LEXICO) {
                sobrou = true;
                break;
            }
        }
        partes.add(sobrou ? "…" : "$");
        return String.join(" ", partes);
    }
}
