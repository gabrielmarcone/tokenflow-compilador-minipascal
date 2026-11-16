package minipascal.sintatico.parser;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de uma analise: os erros encontrados (lexicos, sintaticos e os
 * que as acoes de reducao encontrarem, como os semanticos), em ordem de linha
 * e, na mesma linha, na ordem do texto; os tokens lidos; e o valor produzido
 * pelas acoes de reducao.
 *
 * Como pede o enunciado da Unidade II, a saida e "OK" quando o programa esta
 * correto, ou a lista de erros, cada um com o tipo e a linha.
 */
public final class ResultadoAnalise {

    public static final String OK = "OK";

    private final List<ErroCompilacao> erros;
    private final List<Token> tokens;
    private final Object valor;

    public ResultadoAnalise(List<ErroCompilacao> erros, List<Token> tokens, Object valor) {
        List<ErroCompilacao> ordenados = new ArrayList<>(erros);
        // Por linha e, na mesma linha, pela posicao do token no texto. A ordenacao
        // e estavel: erros com a mesma posicao (ou sem posicao, -1) mantem a ordem
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

    // So os erros de uma fase (lexicos, sintaticos ou semanticos).
    public List<ErroCompilacao> errosDa(CategoriaErro categoria) {
        List<ErroCompilacao> lista = new ArrayList<>();
        for (ErroCompilacao e : erros) {
            if (e.getCategoria() == categoria) {
                lista.add(e);
            }
        }
        return lista;
    }

    // Todos os tokens lidos pelo lexico, inclusive os de erro lexico.
    public List<Token> getTokens() {
        return tokens;
    }

    // Valor do simbolo inicial produzido pelas acoes (null se houve erro sintatico).
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
