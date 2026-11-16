package minipascal.semantico;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uma entrada da tabela de símbolos: um nome declarado, com o que ele é
 * (categoria), o tipo (para variáveis e parâmetros), onde foi declarado
 * (escopo, nível e linha) e, para procedimentos, os parâmetros.
 */
public final class Simbolo {

    private final String nome;
    private final CategoriaSimbolo categoria;
    private final Tipo tipo;
    private final int linha;
    private String escopo = "";
    private int nivel = -1;
    private final List<Simbolo> parametros = new ArrayList<>();

    /**
     * @param nome      como foi escrito na declaração
     * @param categoria o que o nome representa
     * @param tipo      tipo de variáveis e parâmetros (null para procedimentos e programa)
     * @param linha     linha da declaração (0 para os predefinidos)
     */
    public Simbolo(String nome, CategoriaSimbolo categoria, Tipo tipo, int linha) {
        this.nome = nome;
        this.categoria = categoria;
        this.tipo = tipo;
        this.linha = linha;
    }

    // Preenchidos pela TabelaSimbolos quando o símbolo é declarado.
    void registrarEscopo(String escopo, int nivel) {
        this.escopo = escopo;
        this.nivel = nivel;
    }

    // Parâmetros de um procedimento, na ordem da declaração.
    void adicionarParametro(Simbolo parametro) {
        parametros.add(parametro);
    }

    public String getNome() {
        return nome;
    }

    public CategoriaSimbolo getCategoria() {
        return categoria;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public int getLinha() {
        return linha;
    }

    // Nome do escopo da declaração ("externo", "global", "soma", "soma.aux"...).
    public String getEscopo() {
        return escopo;
    }

    // Profundidade do escopo: 0 externo, 1 global, 2 dentro de um procedimento...
    public int getNivel() {
        return nivel;
    }

    public List<Simbolo> getParametros() {
        return Collections.unmodifiableList(parametros);
    }

    // Parâmetros como na declaração: "(var r: integer; x: real)", ou "" se não houver.
    public String descreverParametros() {
        if (parametros.isEmpty()) {
            return "";
        }
        List<String> partes = new ArrayList<>();
        for (Simbolo p : parametros) {
            partes.add((p.categoria == CategoriaSimbolo.PARAMETRO_VAR ? "var " : "") + p.nome + ": " + p.tipo);
        }
        return "(" + String.join("; ", partes) + ")";
    }

    @Override
    public String toString() {
        return nome + " (" + categoria + (tipo == null ? "" : ", " + tipo) + ")";
    }
}
