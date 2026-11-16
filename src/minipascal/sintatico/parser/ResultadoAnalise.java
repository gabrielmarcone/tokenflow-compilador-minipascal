package minipascal.sintatico.parser;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de uma análise: os erros encontrados (léxicos, sintáticos e os
 * que as ações de redução encontrarem, como os semânticos), em ordem de linha
 * e, na mesma linha, na ordem do texto; os tokens lidos; e o valor produzido
 * pelas ações de redução.
 *
 * Como pede o enunciado da Unidade II, a saída é "OK" quando o programa está
 * correto, ou a lista de erros, cada um com o tipo e a linha.
 */
public final class ResultadoAnalise {

    public static final String OK = "OK";

    private final List<ErroCompilacao> erros;
    private final List<Token> tokens;
    private final Object valor;

    public ResultadoAnalise(List<ErroCompilacao> erros, List<Token> tokens, Object valor) {
        List<ErroCompilacao> ordenados = new ArrayList<>(erros);
        // Por linha e, na mesma linha, pela posição do token no texto. A ordenação
        // é estável: erros com a mesma posição (ou sem posição, -1) mantêm a ordem
        // em que foram encontrados.
        ordenados.sort((a, b) -> a.getLinha() != b.getLinha() ? Integer.compare(a.getLinha(), b.getLinha())
                : Integer.compare(a.getPosicao(), b.getPosicao()));
        this.erros = Collections.unmodifiableList(ordenados);
        this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
        this.valor = valor;
    }

    public boolean isSucesso() {
        return erros.isEmpty();
    }

    public List<ErroCompilacao> getErros() {
        return erros;
    }

    public List<ErroCompilacao> errosDa(CategoriaErro categoria) {
        List<ErroCompilacao> lista = new ArrayList<>();
        for (ErroCompilacao e : erros) {
            if (e.getCategoria() == categoria) {
                lista.add(e);
            }
        }
        return lista;
    }

    // Todos os tokens lidos pelo léxico, inclusive os de erro léxico.
    public List<Token> getTokens() {
        return tokens;
    }

    // Valor do símbolo inicial produzido pelas ações (null se houve erro sintático).
    public Object getValor() {
        return valor;
    }

    // "OK", ou um erro por linha.
    public String formatar() {
        if (isSucesso()) {
            return OK;
        }
        List<String> linhas = new ArrayList<>();
        for (ErroCompilacao e : erros) {
            linhas.add(e.toString());
        }
        return String.join("\n", linhas);
    }
}
