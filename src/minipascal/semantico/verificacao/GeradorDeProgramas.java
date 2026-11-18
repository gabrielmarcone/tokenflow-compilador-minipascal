package minipascal.semantico.verificacao;

import minipascal.semantico.Tipo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera programas Mini_Pascal aleatorios e bem tipados, para VerificacaoSemantica.
 *
 * O gerador nao usa o analisador semantico: ele mesmo sabe o tipo de cada
 * expressao que escreve (escreve uma expressao "de um tipo" a partir de regras
 * proprias) e o que e visivel em cada ponto (variaveis globais, parametros,
 * variaveis locais, procedimentos ja declarados, procedimentos aninhados).
 * Por isso serve de oraculo: o programa gerado tem de dar OK.
 *
 * Com um Defeito, um comando (ou uma declaracao) do programa e trocado por um
 * com exatamente aquele erro, e o gerador informa a linha e a mensagem que a
 * analise tem de dar. As mensagens sao montadas aqui, de forma independente
 * do AnalisadorSemantico.
 *
 * Todos os nomes sao diferentes entre si (g0, a1, l2, p3...), entao nao ha
 * nome escondido por outro: o que e visivel e exatamente o que foi declarado
 * no escopo e nos escopos de fora.
 */
final class GeradorDeProgramas {

    enum Defeito {
        ATRIBUICAO_REAL_EM_INTEGER,
        NAO_DECLARADO,
        ARIDADE,
        VAR_SEM_VARIAVEL,
        VAR_DE_OUTRO_TIPO,
        ARGUMENTO_DE_OUTRO_TIPO,
        DIV_COM_REAL,
        CONDICAO_NAO_LOGICA,
        COMPARACAO_INCOMPATIVEL,
        CHAMAR_VARIAVEL,
        PROCEDIMENTO_EM_EXPRESSAO,
        DECLARACAO_REPETIDA,
        READ_SEM_VARIAVEL,
        ESCOPO_INTERNO,
        FOR_VARIAVEL_ALTERADA,
        FOR_LIMITE_DE_OUTRO_TIPO
    }

    private static final Tipo[] DECLARAVEIS = {Tipo.INTEGER, Tipo.REAL, Tipo.CHAR, Tipo.STRING};

    // Uma variavel ou parametro visivel.
    private static final class Var {
        final String nome;
        final Tipo tipo;
        final String descricao; // "uma variavel", "um parametro", "um parametro var"
        final boolean porReferencia;

        Var(String nome, Tipo tipo, String descricao, boolean porReferencia) {
            this.nome = nome;
            this.tipo = tipo;
            this.descricao = descricao;
            this.porReferencia = porReferencia;
        }
    }

    private static final class Proc {
        final String nome;
        final List<Var> parametros = new ArrayList<>();
        final List<Var> locais = new ArrayList<>();
        Proc interno;
        final List<Comando> corpo = new ArrayList<>();

        Proc(String nome) {
            this.nome = nome;
        }
    }

    // O que e visivel num ponto do programa.
    private static final class Contexto {
        final List<Var> vars = new ArrayList<>();
        final List<Proc> procs = new ArrayList<>();
        final List<Proc> invisiveis = new ArrayList<>(); // aninhados em outros procedimentos
    }

    // Um comando do programa (uma linha), no seu contexto.
    private static final class Comando {
        final Contexto contexto;
        String texto;
        boolean defeituoso;

        Comando(Contexto contexto) {
            this.contexto = contexto;
        }
    }

    private final Random rnd;
    private int contador = 0;
    private int linhaDoDefeito = -1;
    private String mensagemDoDefeito;

    GeradorDeProgramas(Random rnd) {
        this.rnd = rnd;
    }

    int getLinhaDoDefeito() {
        return linhaDoDefeito;
    }

    String getMensagemDoDefeito() {
        return mensagemDoDefeito;
    }

    /**
     * @param defeito null para um programa correto
     * @return o programa, ou null se o programa sorteado nao tem onde por o defeito
     */
    String gerar(Defeito defeito) {
        // ---- modelo
        List<Var> globais = new ArrayList<>();
        int nGlobais = 1 + rnd.nextInt(5);
        for (int i = 0; i < nGlobais; i++) {
            globais.add(new Var(nome("g"), sortearTipo(), "uma variável", false));
        }
        List<Proc> procs = new ArrayList<>();
        List<Comando> todos = new ArrayList<>();
        Contexto contextoPrincipal = new Contexto();
        contextoPrincipal.vars.addAll(globais);
        int nProcs = rnd.nextInt(4);
        for (int i = 0; i < nProcs; i++) {
            Proc p = new Proc(nome("p"));
            sortearParametrosELocais(p);
            if (rnd.nextInt(3) == 0) {
                Proc q = new Proc(nome("q"));
                sortearParametrosELocais(q);
                p.interno = q;
                Contexto cq = new Contexto();
                cq.vars.addAll(globais);
                cq.vars.addAll(p.parametros);
                cq.vars.addAll(p.locais);
                cq.vars.addAll(q.parametros);
                cq.vars.addAll(q.locais);
                cq.procs.addAll(procs);
                cq.procs.add(p);
                cq.procs.add(q);
                criarCorpo(q, cq, todos);
                contextoPrincipal.invisiveis.add(q);
            }
            Contexto cp = new Contexto();
            cp.vars.addAll(globais);
            cp.vars.addAll(p.parametros);
            cp.vars.addAll(p.locais);
            cp.procs.addAll(procs);
            cp.procs.add(p);
            if (p.interno != null) {
                cp.procs.add(p.interno);
            }
            criarCorpo(p, cp, todos);
            procs.add(p);
        }
        contextoPrincipal.procs.addAll(procs);
        Proc principal = new Proc("gerado");
        criarCorpo(principal, contextoPrincipal, todos);

        // ---- textos dos comandos (um deles com o defeito)
        boolean declaracaoRepetida = defeito == Defeito.DECLARACAO_REPETIDA;
        Comando comDefeito = null;
        if (defeito != null && !declaracaoRepetida) {
            List<Comando> candidatos = new ArrayList<>();
            for (Comando c : todos) {
                if (podeTer(defeito, c.contexto)) {
                    candidatos.add(c);
                }
            }
            if (candidatos.isEmpty()) {
                return null;
            }
            comDefeito = candidatos.get(rnd.nextInt(candidatos.size()));
            comDefeito.defeituoso = true;
        }
        for (Comando c : todos) {
            c.texto = c.defeituoso ? comandoComDefeito(defeito, c.contexto) : comando(c.contexto, 1);
        }

        // ---- texto do programa
        List<String> linhas = new ArrayList<>();
        linhas.add("program gerado;");
        linhas.add("var");
        int linhaDaPrimeiraGlobal = linhas.size() + 1;
        for (Var g : globais) {
            linhas.add("  " + g.nome + ": " + g.tipo + ";");
        }
        if (declaracaoRepetida) {
            int k = rnd.nextInt(globais.size());
            Var original = globais.get(k);
            linhas.add("  " + original.nome + ": " + sortearTipo() + ";");
            linhaDoDefeito = linhas.size();
            mensagemDoDefeito = "'" + original.nome + "' já foi declarado neste escopo (variável, linha "
                    + (linhaDaPrimeiraGlobal + k) + ")";
        }
        for (Proc p : procs) {
            escreverProcedimento(p, linhas, "", comDefeito);
        }
        linhas.add("begin");
        escreverComandos(principal, linhas, "  ", comDefeito);
        linhas.add("end.");
        return String.join("\n", linhas) + "\n";
    }

    // ------------------------------------------------------------ estrutura

    private void sortearParametrosELocais(Proc p) {
        int nPar = rnd.nextInt(4);
        for (int i = 0; i < nPar; i++) {
            boolean var = rnd.nextInt(5) < 2;
            p.parametros.add(new Var(nome("a"), sortearTipo(), var ? "um parâmetro var" : "um parâmetro", var));
        }
        int nLoc = rnd.nextInt(3);
        for (int i = 0; i < nLoc; i++) {
            p.locais.add(new Var(nome("l"), sortearTipo(), "uma variável", false));
        }
    }

    private void criarCorpo(Proc p, Contexto c, List<Comando> todos) {
        int n = 1 + rnd.nextInt(5);
        for (int i = 0; i < n; i++) {
            Comando cmd = new Comando(c);
            p.corpo.add(cmd);
            todos.add(cmd);
        }
    }

    private void escreverProcedimento(Proc p, List<String> linhas, String recuo, Comando comDefeito) {
        StringBuilder cab = new StringBuilder(recuo + "procedure " + p.nome);
        if (!p.parametros.isEmpty()) {
            List<String> partes = new ArrayList<>();
            for (Var a : p.parametros) {
                partes.add((a.porReferencia ? "var " : "") + a.nome + ": " + a.tipo);
            }
            cab.append("(").append(String.join("; ", partes)).append(")");
        }
        linhas.add(cab.append(";").toString());
        if (!p.locais.isEmpty()) {
            linhas.add(recuo + "var");
            for (Var l : p.locais) {
                linhas.add(recuo + "  " + l.nome + ": " + l.tipo + ";");
            }
        }
        if (p.interno != null) {
            escreverProcedimento(p.interno, linhas, recuo + "  ", comDefeito);
        }
        linhas.add(recuo + "begin");
        escreverComandos(p, linhas, recuo + "  ", comDefeito);
        linhas.add(recuo + "end;");
    }

    private void escreverComandos(Proc p, List<String> linhas, String recuo, Comando comDefeito) {
        for (Comando c : p.corpo) {
            linhas.add(recuo + c.texto + ";");
            if (c == comDefeito) {
                linhaDoDefeito = linhas.size();
            }
        }
    }

    // -------------------------------------------------------------- comandos

    // Um comando correto (sem o ';').
    private String comando(Contexto c, int profundidade) {
        while (true) {
            switch (rnd.nextInt(profundidade > 0 ? 10 : 5)) {
                case 0:
                    if (!c.vars.isEmpty()) {
                        Var v = sortear(c.vars);
                        return v.nome + " := " + expressaoAtribuivelA(v.tipo, c, 2);
                    }
                    break;
                case 1: {
                    List<Proc> chamaveis = chamaveis(c);
                    if (!chamaveis.isEmpty()) {
                        Proc p = sortear(chamaveis);
                        return chamada(p, argumentosCorretos(p, c));
                    }
                    break;
                }
                case 2:
                    if (!c.vars.isEmpty()) {
                        List<String> args = new ArrayList<>();
                        int n = 1 + rnd.nextInt(2);
                        for (int i = 0; i < n; i++) {
                            args.add(sortear(c.vars).nome);
                        }
                        return "read(" + String.join(", ", args) + ")";
                    }
                    break;
                case 3:
                    return (rnd.nextBoolean() ? "write(" : "writeln(") + listaDeExpressoes(c) + ")";
                case 4:
                    return "writeln";
                case 5:
                    return "if " + expressao(Tipo.LOGICO, c, 2) + " then " + comando(c, 0);
                case 6:
                    return "if " + expressao(Tipo.LOGICO, c, 2) + " then " + comando(c, 0) + " else " + comando(c, 0);
                case 7:
                    return "while " + expressao(Tipo.LOGICO, c, 2) + " do " + comando(c, 0);
                case 8: {
                    // for com uma variavel integer ou char; o corpo nao pode alterar
                    // a variavel, entao ele nem a enxerga (ver semControle).
                    List<Var> ordinais = ordinais(c.vars);
                    if (!ordinais.isEmpty()) {
                        Var v = sortear(ordinais);
                        return cabecalhoDoFor(v, c, v.tipo, v.tipo) + comando(semControle(c, v), 0);
                    }
                    break;
                }
                default:
                    return "begin " + comando(c, 0) + "; " + comando(c, 0) + "; end";
            }
        }
    }

    // "for v := inicio to fim do " (ou downto), com limites dos tipos dados.
    private String cabecalhoDoFor(Var v, Contexto c, Tipo inicio, Tipo fim) {
        return "for " + v.nome + " := " + expressao(inicio, c, 1) + (rnd.nextBoolean() ? " to " : " downto ")
                + expressao(fim, c, 1) + " do ";
    }

    // Variaveis que podem controlar um for: integer e char.
    private static List<Var> ordinais(List<Var> vars) {
        List<Var> r = new ArrayList<>();
        for (Var v : vars) {
            if (v.tipo == Tipo.INTEGER || v.tipo == Tipo.CHAR) {
                r.add(v);
            }
        }
        return r;
    }

    // O mesmo contexto sem a variavel de controle (para o corpo do for nao altera-la).
    private static Contexto semControle(Contexto c, Var controle) {
        Contexto r = new Contexto();
        for (Var v : c.vars) {
            if (!v.nome.equals(controle.nome)) {
                r.vars.add(v);
            }
        }
        r.procs.addAll(c.procs);
        r.invisiveis.addAll(c.invisiveis);
        return r;
    }

    private boolean podeTer(Defeito d, Contexto c) {
        switch (d) {
            case ATRIBUICAO_REAL_EM_INTEGER:
                return !daqueleTipo(c.vars, Tipo.INTEGER).isEmpty();
            case ARIDADE:
            case PROCEDIMENTO_EM_EXPRESSAO:
                return !c.procs.isEmpty() && (d != Defeito.ARIDADE || !chamaveis(c).isEmpty());
            case VAR_SEM_VARIAVEL:
                return !comParametro(chamaveis(c), true).isEmpty();
            case VAR_DE_OUTRO_TIPO:
                for (Proc p : comParametro(chamaveis(c), true)) {
                    for (Var a : p.parametros) {
                        if (a.porReferencia && temDeOutroTipo(c.vars, a.tipo)) {
                            return true;
                        }
                    }
                }
                return false;
            case ARGUMENTO_DE_OUTRO_TIPO:
                return !comParametro(chamaveis(c), false).isEmpty();
            case CHAMAR_VARIAVEL:
            case READ_SEM_VARIAVEL:
                return !c.vars.isEmpty();
            case ESCOPO_INTERNO:
                return !c.invisiveis.isEmpty();
            case FOR_VARIAVEL_ALTERADA:
            case FOR_LIMITE_DE_OUTRO_TIPO:
                return !ordinais(c.vars).isEmpty();
            default:
                return true;
        }
    }

    // Um comando com o defeito (e so ele); guarda a mensagem esperada.
    private String comandoComDefeito(Defeito d, Contexto c) {
        switch (d) {
            case ATRIBUICAO_REAL_EM_INTEGER: {
                Var v = sortear(daqueleTipo(c.vars, Tipo.INTEGER));
                mensagemDoDefeito = "Tipos incompatíveis na atribuição a '" + v.nome
                        + "': a variável é integer e a expressão é real";
                return v.nome + " := " + expressao(Tipo.REAL, c, 2);
            }
            case NAO_DECLARADO:
                mensagemDoDefeito = "Identificador 'inexistente' não declarado";
                return rnd.nextBoolean() ? "writeln(inexistente)" : "inexistente := " + expressao(sortearTipo(), c, 1);
            case ARIDADE: {
                Proc p = sortear(chamaveis(c));
                List<String> args = argumentosCorretos(p, c);
                if (args.isEmpty() || rnd.nextBoolean()) {
                    args.add(String.valueOf(rnd.nextInt(10)));
                } else {
                    args.remove(args.size() - 1);
                }
                mensagemDoDefeito = "O procedimento '" + p.nome + "' tem " + quantos(p.parametros.size(), "parâmetro")
                        + ", mas a chamada passa " + quantos(args.size(), "argumento");
                return chamada(p, args);
            }
            case VAR_SEM_VARIAVEL: {
                Proc p = sortear(comParametro(chamaveis(c), true));
                List<String> args = argumentosCorretos(p, c);
                int i = indiceDeParametro(p, true);
                Var a = p.parametros.get(i);
                // Entre parenteses, ou uma constante: nao e mais uma variavel.
                args.set(i, rnd.nextBoolean() ? "(" + args.get(i) + ")" : literal(a.tipo));
                mensagemDoDefeito = "Argumento " + (i + 1) + " de '" + p.nome + "': o parâmetro '" + a.nome
                        + "' é var e precisa receber uma variável";
                return chamada(p, args);
            }
            case VAR_DE_OUTRO_TIPO: {
                while (true) {
                    Proc p = sortear(comParametro(chamaveis(c), true));
                    int i = indiceDeParametro(p, true);
                    Var a = p.parametros.get(i);
                    List<Var> outros = deOutroTipo(c.vars, a.tipo);
                    if (outros.isEmpty()) {
                        continue;
                    }
                    Var v = sortear(outros);
                    List<String> args = argumentosCorretos(p, c);
                    args.set(i, v.nome);
                    mensagemDoDefeito = "Argumento " + (i + 1) + " de '" + p.nome + "': o parâmetro var '" + a.nome
                            + "' é " + a.tipo + " e a variável '" + v.nome + "' é " + v.tipo
                            + " (num parâmetro var os tipos devem ser iguais)";
                    return chamada(p, args);
                }
            }
            case ARGUMENTO_DE_OUTRO_TIPO: {
                Proc p = sortear(comParametro(chamaveis(c), false));
                int i = indiceDeParametro(p, false);
                Var a = p.parametros.get(i);
                List<Tipo> errados = new ArrayList<>();
                for (Tipo t : new Tipo[]{Tipo.INTEGER, Tipo.REAL, Tipo.CHAR, Tipo.STRING, Tipo.LOGICO}) {
                    if (!atribuivel(t, a.tipo)) {
                        errados.add(t);
                    }
                }
                Tipo t = sortear(errados);
                List<String> args = argumentosCorretos(p, c);
                args.set(i, expressao(t, c, 2));
                mensagemDoDefeito = "Argumento " + (i + 1) + " de '" + p.nome + "': o parâmetro '" + a.nome + "' é "
                        + a.tipo + " e o argumento é " + t;
                return chamada(p, args);
            }
            case DIV_COM_REAL: {
                String op = rnd.nextBoolean() ? "div" : "mod";
                boolean realAntes = rnd.nextBoolean();
                String real = embrulhar(expressao(Tipo.REAL, c, 1));
                String inteiro = embrulhar(expressao(Tipo.INTEGER, c, 1));
                mensagemDoDefeito = "O operador '" + op + "' exige operandos integer, mas recebeu "
                        + (realAntes ? "real e integer" : "integer e real");
                return "writeln(" + (realAntes ? real + " " + op + " " + inteiro : inteiro + " " + op + " " + real) + ")";
            }
            case CONDICAO_NAO_LOGICA: {
                Tipo t = sortearTipo();
                boolean ehIf = rnd.nextBoolean();
                mensagemDoDefeito = "A condição do '" + (ehIf ? "if" : "while") + "' deve ser lógica (uma comparação), mas é "
                        + t;
                String cond = expressao(t, c, 2);
                return ehIf ? "if " + cond + " then " + comando(c, 0) : "while " + cond + " do " + comando(c, 0);
            }
            case COMPARACAO_INCOMPATIVEL: {
                Tipo[][] pares = {{Tipo.INTEGER, Tipo.STRING}, {Tipo.REAL, Tipo.CHAR}, {Tipo.CHAR, Tipo.INTEGER},
                        {Tipo.STRING, Tipo.REAL}, {Tipo.LOGICO, Tipo.INTEGER}, {Tipo.STRING, Tipo.LOGICO}};
                Tipo[] par = pares[rnd.nextInt(pares.length)];
                String op = sortear(new String[]{"=", "<", ">", "<>", "<=", ">="});
                mensagemDoDefeito = "Comparação entre tipos incompatíveis: " + par[0] + " e " + par[1];
                return "if " + embrulhar(expressao(par[0], c, 1)) + " " + op + " " + embrulhar(expressao(par[1], c, 1))
                        + " then writeln";
            }
            case CHAMAR_VARIAVEL: {
                Var v = sortear(c.vars);
                mensagemDoDefeito = "'" + v.nome + "' é " + v.descricao + " do tipo " + v.tipo
                        + " e não pode ser chamado como procedimento";
                return v.nome;
            }
            case PROCEDIMENTO_EM_EXPRESSAO: {
                Proc p = sortear(c.procs);
                mensagemDoDefeito = "'" + p.nome + "' é um procedimento e não pode ser usado em uma expressão";
                return "writeln(" + p.nome + ")";
            }
            case READ_SEM_VARIAVEL: {
                List<String> args = new ArrayList<>();
                int n = 1 + rnd.nextInt(3);
                for (int i = 0; i < n; i++) {
                    args.add(sortear(c.vars).nome);
                }
                int i = rnd.nextInt(n);
                args.set(i, rnd.nextBoolean() ? "(" + args.get(i) + ")" : literal(sortearTipo()));
                mensagemDoDefeito = "Argumento " + (i + 1) + " de 'read' precisa ser uma variável";
                return "read(" + String.join(", ", args) + ")";
            }
            case ESCOPO_INTERNO: {
                Proc q = sortear(c.invisiveis);
                mensagemDoDefeito = "Identificador '" + q.nome + "' não declarado";
                return q.nome;
            }
            case FOR_VARIAVEL_ALTERADA: {
                // atribuicao a variavel de controle dentro do laco
                Var v = sortear(ordinais(c.vars));
                mensagemDoDefeito = "'" + v.nome + "' é a variável de controle de um 'for' e não pode ser alterada dentro dele";
                return cabecalhoDoFor(v, c, v.tipo, v.tipo) + v.nome + " := " + expressaoAtribuivelA(v.tipo, c, 1);
            }
            case FOR_LIMITE_DE_OUTRO_TIPO: {
                Var v = sortear(ordinais(c.vars));
                List<Tipo> errados = new ArrayList<>();
                for (Tipo t : new Tipo[]{Tipo.INTEGER, Tipo.REAL, Tipo.CHAR, Tipo.STRING, Tipo.LOGICO}) {
                    if (t != v.tipo) {
                        errados.add(t);
                    }
                }
                Tipo errado = sortear(errados);
                boolean inicial = rnd.nextBoolean();
                mensagemDoDefeito = "O valor " + (inicial ? "inicial" : "final") + " do 'for' deve ser " + v.tipo
                        + ", como a variável '" + v.nome + "', mas é " + errado;
                return cabecalhoDoFor(v, c, inicial ? errado : v.tipo, inicial ? v.tipo : errado)
                        + comando(semControle(c, v), 0);
            }
            default:
                throw new IllegalArgumentException(d.toString());
        }
    }

    // ---------------------------------------------------- chamadas e argumentos

    // Procedimentos que da para chamar: para cada parametro var ha uma variavel do mesmo tipo.
    private List<Proc> chamaveis(Contexto c) {
        List<Proc> lista = new ArrayList<>();
        for (Proc p : c.procs) {
            boolean ok = true;
            for (Var a : p.parametros) {
                if (a.porReferencia && daqueleTipo(c.vars, a.tipo).isEmpty()) {
                    ok = false;
                }
            }
            if (ok) {
                lista.add(p);
            }
        }
        return lista;
    }

    private static List<Proc> comParametro(List<Proc> procs, boolean porReferencia) {
        List<Proc> lista = new ArrayList<>();
        for (Proc p : procs) {
            for (Var a : p.parametros) {
                if (a.porReferencia == porReferencia) {
                    lista.add(p);
                    break;
                }
            }
        }
        return lista;
    }

    private int indiceDeParametro(Proc p, boolean porReferencia) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < p.parametros.size(); i++) {
            if (p.parametros.get(i).porReferencia == porReferencia) {
                indices.add(i);
            }
        }
        return indices.get(rnd.nextInt(indices.size()));
    }

    private List<String> argumentosCorretos(Proc p, Contexto c) {
        List<String> args = new ArrayList<>();
        for (Var a : p.parametros) {
            args.add(a.porReferencia ? sortear(daqueleTipo(c.vars, a.tipo)).nome : expressaoAtribuivelA(a.tipo, c, 2));
        }
        return args;
    }

    private static String chamada(Proc p, List<String> args) {
        return args.isEmpty() ? p.nome : p.nome + "(" + String.join(", ", args) + ")";
    }

    private String listaDeExpressoes(Contexto c) {
        List<String> lista = new ArrayList<>();
        int n = 1 + rnd.nextInt(3);
        for (int i = 0; i < n; i++) {
            Tipo[] todos = {Tipo.INTEGER, Tipo.REAL, Tipo.CHAR, Tipo.STRING, Tipo.LOGICO};
            lista.add(expressao(todos[rnd.nextInt(todos.length)], c, 2));
        }
        return String.join(", ", lista);
    }

    // ------------------------------------------------------------ expressoes

    // Uma expressao que pode ser atribuida a uma variavel do tipo dado.
    private String expressaoAtribuivelA(Tipo destino, Contexto c, int profundidade) {
        if (destino == Tipo.REAL && rnd.nextInt(3) == 0) {
            return expressao(Tipo.INTEGER, c, profundidade);
        }
        if (destino == Tipo.STRING && rnd.nextInt(3) == 0) {
            return expressao(Tipo.CHAR, c, profundidade);
        }
        return expressao(destino, c, profundidade);
    }

    // Uma expressao exatamente do tipo dado.
    private String expressao(Tipo t, Contexto c, int profundidade) {
        if (t == Tipo.LOGICO) {
            if (profundidade > 0 && rnd.nextInt(5) == 0) {
                return embrulhar(expressao(Tipo.LOGICO, c, profundidade - 1)) + " " + relacional() + " "
                        + embrulhar(expressao(Tipo.LOGICO, c, profundidade - 1));
            }
            // and, or e not: so com operandos logicos, e o resultado e logico.
            // Os operandos compostos vao entre parenteses (no Pascal, and/or
            // ligam com mais forca que as comparacoes).
            if (profundidade > 0 && rnd.nextInt(4) == 0) {
                int p = profundidade - 1;
                switch (rnd.nextInt(3)) {
                    case 0:
                        return "not " + embrulhar(expressao(Tipo.LOGICO, c, p));
                    default:
                        return embrulhar(expressao(Tipo.LOGICO, c, p)) + (rnd.nextBoolean() ? " and " : " or ")
                                + embrulhar(expressao(Tipo.LOGICO, c, p));
                }
            }
            boolean numeros = rnd.nextBoolean();
            Tipo a = numeros ? (rnd.nextBoolean() ? Tipo.INTEGER : Tipo.REAL) : (rnd.nextBoolean() ? Tipo.CHAR : Tipo.STRING);
            Tipo b = numeros ? (rnd.nextBoolean() ? Tipo.INTEGER : Tipo.REAL) : (rnd.nextBoolean() ? Tipo.CHAR : Tipo.STRING);
            int p = Math.max(0, profundidade - 1);
            return embrulhar(expressao(a, c, p)) + " " + relacional() + " " + embrulhar(expressao(b, c, p));
        }
        if (profundidade == 0 || t == Tipo.CHAR || rnd.nextInt(3) == 0) {
            return atomo(t, c);
        }
        int p = profundidade - 1;
        switch (t) {
            case INTEGER: {
                if (rnd.nextInt(6) == 0) {
                    return sortear(new String[]{"- ", "+ "}) + embrulhar(expressao(Tipo.INTEGER, c, p));
                }
                String op = sortear(new String[]{"+", "-", "*", "div", "mod"});
                return embrulhar(expressao(Tipo.INTEGER, c, p)) + " " + op + " " + embrulhar(expressao(Tipo.INTEGER, c, p));
            }
            case REAL: {
                if (rnd.nextInt(6) == 0) {
                    return "- " + embrulhar(expressao(Tipo.REAL, c, p));
                }
                // Pelo menos um dos lados real.
                Tipo a = Tipo.REAL;
                Tipo b = rnd.nextBoolean() ? Tipo.REAL : Tipo.INTEGER;
                if (rnd.nextBoolean()) {
                    Tipo x = a;
                    a = b;
                    b = x;
                }
                // '/' aceita quaisquer numeros e sempre da real.
                if (rnd.nextInt(4) == 0) {
                    Tipo x = rnd.nextBoolean() ? Tipo.REAL : Tipo.INTEGER;
                    Tipo y = rnd.nextBoolean() ? Tipo.REAL : Tipo.INTEGER;
                    return embrulhar(expressao(x, c, p)) + " / " + embrulhar(expressao(y, c, p));
                }
                String op = sortear(new String[]{"+", "-", "*"});
                return embrulhar(expressao(a, c, p)) + " " + op + " " + embrulhar(expressao(b, c, p));
            }
            default: { // STRING: texto + texto
                Tipo a = rnd.nextBoolean() ? Tipo.STRING : Tipo.CHAR;
                Tipo b = rnd.nextBoolean() ? Tipo.STRING : Tipo.CHAR;
                return embrulhar(expressao(a, c, p)) + " + " + embrulhar(expressao(b, c, p));
            }
        }
    }

    private String atomo(Tipo t, Contexto c) {
        List<Var> vars = daqueleTipo(c.vars, t);
        if (!vars.isEmpty() && rnd.nextBoolean()) {
            return sortear(vars).nome;
        }
        return literal(t);
    }

    private String literal(Tipo t) {
        switch (t) {
            case INTEGER:
                return String.valueOf(rnd.nextInt(100));
            case REAL:
                return sortear(new String[]{"1.5", "0.25", "3.0", "2.5e1", "7.75"});
            case CHAR:
                return sortear(new String[]{"'a'", "'z'", "'0'", "' '"});
            case STRING:
                return sortear(new String[]{"\"texto\"", "\"\"", "\"fim\"", "\"a b\""});
            default:
                return "(1 < 2)";
        }
    }

    private String relacional() {
        return sortear(new String[]{"=", "<", ">", "<>", "<=", ">="});
    }

    // Uma expressao composta vai entre parenteses quando e parte de outra.
    private static String embrulhar(String expressao) {
        return ehAtomo(expressao) ? expressao : "(" + expressao + ")";
    }

    // Um nome, um numero, uma constante char ou string sozinha, ou algo ja entre parenteses do comeco ao fim.
    private static boolean ehAtomo(String e) {
        int n = e.length();
        if (n >= 2 && e.charAt(0) == '"' && e.indexOf('"', 1) == n - 1) {
            return true;
        }
        if (n == 3 && e.charAt(0) == '\'' && e.charAt(2) == '\'') {
            return true;
        }
        return !e.contains(" ");
    }

    // ----------------------------------------------------------------- apoio

    // Regras de atribuicao do gerador (independentes de Tipo.podeSerAtribuidoA).
    private static boolean atribuivel(Tipo valor, Tipo destino) {
        if (valor == destino) {
            return true;
        }
        return (valor == Tipo.INTEGER && destino == Tipo.REAL) || (valor == Tipo.CHAR && destino == Tipo.STRING);
    }

    private static List<Var> daqueleTipo(List<Var> vars, Tipo t) {
        List<Var> lista = new ArrayList<>();
        for (Var v : vars) {
            if (v.tipo == t) {
                lista.add(v);
            }
        }
        return lista;
    }

    private static List<Var> deOutroTipo(List<Var> vars, Tipo t) {
        List<Var> lista = new ArrayList<>();
        for (Var v : vars) {
            if (v.tipo != t) {
                lista.add(v);
            }
        }
        return lista;
    }

    private static boolean temDeOutroTipo(List<Var> vars, Tipo t) {
        return !deOutroTipo(vars, t).isEmpty();
    }

    private static String quantos(int n, String palavra) {
        return n + " " + palavra + (n == 1 ? "" : "s");
    }

    private Tipo sortearTipo() {
        return DECLARAVEIS[rnd.nextInt(DECLARAVEIS.length)];
    }

    private <T> T sortear(List<T> lista) {
        return lista.get(rnd.nextInt(lista.size()));
    }

    private <T> T sortear(T[] lista) {
        return lista[rnd.nextInt(lista.length)];
    }

    private String nome(String prefixo) {
        return prefixo + (contador++);
    }
}
