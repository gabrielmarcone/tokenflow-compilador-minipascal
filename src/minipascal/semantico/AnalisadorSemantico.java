package minipascal.semantico;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.TipoToken;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.parser.AcoesReducao;
import minipascal.sintatico.parser.Terminal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Analise semantica, feita durante a analise sintatica: estas sao as acoes
 * executadas a cada reducao do SLR(1) (ver AcoesReducao). E uma definicao
 * S-atribuida: o atributo de cada simbolo da pilha e calculado a partir dos
 * filhos. Terminais levam o proprio Terminal; LISTA_ID e LISTA_ID_PARAM, os
 * nomes; PALIST, LISTA_PARAM e PARAM, os parametros declarados; CAB_PROC, o
 * procedimento; EXPR, SIEXPR, TERMO e FATOR, uma Expressao (tipo, linha e,
 * se for so uma variavel, qual); LISTA_ARG, os argumentos; os demais, null.
 *
 * Escopos (ver TabelaSimbolos): read, write, writeln e o nome do programa
 * ficam no escopo externo; as variaveis sao declaradas ao reduzir DECL_VAR;
 * ao reduzir CAB_PROC (antes do bloco), o procedimento e declarado e o escopo
 * dele e aberto com os parametros; no fim do bloco do procedimento, o escopo
 * e fechado. Como o procedimento existe antes do bloco, a recursao funciona.
 *
 * Verificacoes: nomes (nao declarado, repetido no escopo, procedimento usado
 * como variavel e o contrario); tipos na atribuicao, nos operadores e nas
 * comparacoes; condicao de if e while logica; chamadas (quantidade e tipo dos
 * argumentos, parametro var recebendo variavel do mesmo tipo); read, write e
 * writeln; for (variavel de controle integer ou char, limites do mesmo tipo
 * dela, e a variavel nao pode ser alterada dentro do laco). A regra de cada
 * operador esta junto do metodo que a confere.
 *
 * Sem cascatas: expressao com erro tem o tipo ERRO, que nao gera novas
 * mensagens; nome nao declarado e relatado uma vez por escopo. Depois do
 * primeiro erro sintatico a semantica para: a recuperacao pode descartar
 * declaracoes, e conferir o resto geraria erros que nao existem no programa.
 */
public final class AnalisadorSemantico implements AcoesReducao {

    // ------------------------------------------------------------ atributos

    // Atributo de EXPR, SIEXPR, TERMO e FATOR.
    private static final class Expressao {
        final Tipo tipo;
        final int linha;
        final int posicao;
        // A variavel, quando a expressao e so ela (pode ir para um parametro var ou para read).
        final Simbolo variavel;

        Expressao(Tipo tipo, int linha, int posicao, Simbolo variavel) {
            this.tipo = tipo;
            this.linha = linha;
            this.posicao = posicao;
            this.variavel = variavel;
        }

        Expressao comTipo(Tipo novo) {
            return new Expressao(novo, linha, posicao, null);
        }
    }

    // Atributo de PARAM, LISTA_PARAM e PALIST: um parametro como foi declarado.
    private static final class ParametroDeclarado {
        final Terminal nome;
        final Tipo tipo;
        final boolean porReferencia;

        ParametroDeclarado(Terminal nome, Tipo tipo, boolean porReferencia) {
            this.nome = nome;
            this.tipo = tipo;
            this.porReferencia = porReferencia;
        }
    }

    // A acao de uma producao: recebe os atributos do corpo e devolve o da cabeca.
    private interface Regra {
        Object aplicar(List<Object> v, int linha);
    }

    // --------------------------------------------------------------- estado

    private final TabelaSimbolos tabela = new TabelaSimbolos();
    private final Map<String, Regra> regras = new HashMap<>();
    private final List<ErroCompilacao> erros = new ArrayList<>();
    private boolean interrompida = false;

    // Ultimo terminal empilhado (para reconhecer o nome do programa, logo depois de 'program').
    private Terminal anterior;
    // Ultima EXPR reduzida: ao empilhar 'then' ou 'do', e a da condicao.
    private Expressao ultimaExpressao;
    // A ultima reducao foi a de uma EXPR? (no 'do' do for, a ultima e a de CAB_FOR)
    private boolean ultimaReducaoFoiExpr;
    // Variaveis de controle dos for em andamento (do mais externo ao mais interno).
    private final Deque<Simbolo> controlesDoFor = new ArrayDeque<>();

    /**
     * @param gramatica a gramatica do analisador sintatico; toda producao dela
     *                  precisa ter uma regra aqui, e toda regra, uma producao
     *                  (se nao, IllegalArgumentException: a gramatica mudou e a
     *                  semantica nao acompanhou)
     */
    public AnalisadorSemantico(Gramatica gramatica) {
        registrarRegras();
        List<String> problemas = conferirCobertura(gramatica);
        if (!problemas.isEmpty()) {
            throw new IllegalArgumentException("a análise semântica não corresponde à gramática: " + problemas);
        }
        // Os predefinidos ficam no escopo externo (nivel 0), fora do programa.
        for (String nome : new String[]{"read", "write", "writeln"}) {
            tabela.declarar(new Simbolo(nome, CategoriaSimbolo.PROCEDIMENTO_PREDEFINIDO, null, 0));
        }
    }

    // Producoes sem regra e regras sem producao (vazio quando correspondem).
    List<String> conferirCobertura(Gramatica gramatica) {
        TreeSet<String> semRegra = new TreeSet<>();
        TreeSet<String> semProducao = new TreeSet<>(regras.keySet());
        for (Producao p : gramatica.getProducoes()) {
            if (p.getNumero() == 0) {
                continue; // S' -> PROG: nunca e reduzida (e a aceitacao)
            }
            String chave = p.toString();
            if (!regras.containsKey(chave)) {
                semRegra.add("sem regra: " + chave);
            }
            semProducao.remove(chave);
        }
        List<String> problemas = new ArrayList<>(semRegra);
        for (String r : semProducao) {
            problemas.add("regra sem produção: " + r);
        }
        return problemas;
    }

    public TabelaSimbolos getTabela() {
        return tabela;
    }

    // Todos os erros semanticos encontrados, na ordem em que foram encontrados.
    @Override
    public List<ErroCompilacao> getErros() {
        return Collections.unmodifiableList(erros);
    }

    // Verdadeiro se a analise parou num erro sintatico (ver o comentario da classe).
    public boolean isInterrompida() {
        return interrompida;
    }

    // ------------------------------------------------ ganchos do analisador

    @Override
    public Object aoEmpilhar(Terminal terminal) {
        if (interrompida) {
            return terminal;
        }
        if (terminal.getNome().equals(Terminal.ID) && anterior != null && anterior.getNome().equals("program")) {
            // O nome do programa fica no escopo externo: nao conflita com os nomes
            // do programa (como no Pascal ISO) e nao pode ser usado como variavel.
            // Se coincidir com read, write ou writeln, fica so o predefinido.
            tabela.declarar(new Simbolo(terminal.getLexema(), CategoriaSimbolo.PROGRAMA, null, terminal.getLinha()));
            tabela.abrirEscopo(TabelaSimbolos.ESCOPO_GLOBAL);
        }
        if ((terminal.getNome().equals("then") || terminal.getNome().equals("do")) && ultimaReducaoFoiExpr) {
            // 'then' e 'do' aparecem depois da condicao (if EXPR then, while EXPR do).
            // Num analisador LR, o 'then' so e empilhado depois de a condicao ser
            // reduzida a EXPR, e essa e a ultima reducao antes dele: a condicao e a
            // ultima EXPR reduzida. Conferir aqui, e nao na reducao do if inteiro,
            // faz o erro da condicao sair antes dos erros do corpo. No 'do' do for
            // a ultima reducao e a de CAB_FOR, e nao ha condicao a conferir.
            verificarCondicao(ultimaExpressao, terminal.getNome().equals("then") ? "if" : "while");
        }
        anterior = terminal;
        return terminal;
    }

    @Override
    public Object aoReduzir(Producao producao, List<Object> valores, int linha) {
        if (interrompida) {
            return null;
        }
        // A regra da producao recebe os atributos do corpo e devolve o da cabeca.
        Regra regra = regras.get(producao.toString());
        Object valor = regra.aplicar(valores, linha);
        // guarda a ultima EXPR: se o proximo terminal for then/do, ela e a condicao
        ultimaReducaoFoiExpr = producao.getCabeca().equals("EXPR");
        if (ultimaReducaoFoiExpr) {
            ultimaExpressao = (Expressao) valor;
        }
        return valor;
    }

    @Override
    public void aoErroSintatico(Terminal encontrado) {
        interrompida = true;
    }

    // ---------------------------------------------------------------- regras

    private void regra(String producao, Regra regra) {
        if (regras.put(producao, regra) != null) {
            throw new IllegalStateException("regra repetida: " + producao);
        }
    }

    // Producoes sem efeito semantico (estrutura de blocos e comandos).
    private void semAcao(String... producoes) {
        for (String p : producoes) {
            regra(p, (v, linha) -> null);
        }
    }

    // Uma regra para cada producao de gramatica.txt (conferido em conferirCobertura).
    private void registrarRegras() {
        // Programa, blocos e listas de comandos
        semAcao("PROG -> program id ; BLOCO .",
                "BLOCO -> BLOCO_VAR BLOCO_PROC begin LISTA_CMD end",
                "BLOCO_VAR -> BLOCO_VAR var DECL_VAR",
                "BLOCO_VAR -> ε",
                "BLOCO_PROC -> ε",
                "LISTA_CMD -> LISTA_CMD COMANDO ;",
                "LISTA_CMD -> COMANDO ;");

        // Declaracoes de variaveis
        regra("DECL_VAR -> DECL_VAR LISTA_ID : tipo ;", (v, linha) -> declararVariaveis(v.get(1), v.get(3)));
        regra("DECL_VAR -> LISTA_ID : tipo ;", (v, linha) -> declararVariaveis(v.get(0), v.get(2)));
        regra("LISTA_ID -> LISTA_ID , id", (v, linha) -> mais(v.get(0), v.get(2)));
        regra("LISTA_ID -> id", (v, linha) -> mais(new ArrayList<Terminal>(), v.get(0)));

        // Procedimentos e parametros
        regra("BLOCO_PROC -> BLOCO_PROC CAB_PROC ; BLOCO ;", (v, linha) -> {
            tabela.fecharEscopo();
            return null;
        });
        regra("CAB_PROC -> procedure id PALIST", (v, linha) -> declararProcedimento((Terminal) v.get(1), v.get(2)));
        regra("PALIST -> ( LISTA_PARAM )", (v, linha) -> v.get(1));
        regra("PALIST -> ε", (v, linha) -> new ArrayList<ParametroDeclarado>());
        regra("LISTA_PARAM -> LISTA_PARAM ; PARAM", (v, linha) -> juntar(v.get(0), v.get(2)));
        regra("LISTA_PARAM -> PARAM", (v, linha) -> v.get(0));
        regra("PARAM -> LISTA_ID_PARAM : tipo", (v, linha) -> parametros(v.get(0), v.get(2), false));
        regra("PARAM -> var LISTA_ID_PARAM : tipo", (v, linha) -> parametros(v.get(1), v.get(3), true));
        regra("LISTA_ID_PARAM -> LISTA_ID_PARAM , id", (v, linha) -> mais(v.get(0), v.get(2)));
        regra("LISTA_ID_PARAM -> id", (v, linha) -> mais(new ArrayList<Terminal>(), v.get(0)));

        // Comandos (a condicao de if e while e conferida em aoEmpilhar, no 'then' e no 'do')
        semAcao("COMANDO -> CMD_FECHADO",
                "COMANDO -> CMD_ABERTO",
                "CMD_FECHADO -> SIMPLES",
                "CMD_FECHADO -> if EXPR then CMD_FECHADO else CMD_FECHADO",
                "CMD_FECHADO -> while EXPR do CMD_FECHADO",
                "CMD_ABERTO -> if EXPR then COMANDO",
                "CMD_ABERTO -> if EXPR then CMD_FECHADO else CMD_ABERTO",
                "CMD_ABERTO -> while EXPR do CMD_ABERTO",
                "SIMPLES -> begin LISTA_CMD end");
        // for: o cabecalho e conferido ao reduzir CAB_FOR, antes do corpo; no fim
        // do laco (a reducao do comando inteiro), a variavel volta a poder mudar.
        regra("CAB_FOR -> for id := EXPR to EXPR", (v, linha) -> cabecalhoDoFor(v));
        regra("CAB_FOR -> for id := EXPR downto EXPR", (v, linha) -> cabecalhoDoFor(v));
        regra("CMD_FECHADO -> CAB_FOR do CMD_FECHADO", (v, linha) -> fimDoFor());
        regra("CMD_ABERTO -> CAB_FOR do CMD_ABERTO", (v, linha) -> fimDoFor());
        regra("SIMPLES -> id := EXPR", (v, linha) -> {
            atribuir((Terminal) v.get(0), (Expressao) v.get(2));
            return null;
        });
        regra("SIMPLES -> id", (v, linha) -> {
            chamar((Terminal) v.get(0), new ArrayList<Expressao>());
            return null;
        });
        regra("SIMPLES -> id ( LISTA_ARG )", (v, linha) -> {
            chamar((Terminal) v.get(0), argumentos(v.get(2)));
            return null;
        });
        regra("LISTA_ARG -> LISTA_ARG , EXPR", (v, linha) -> mais(v.get(0), v.get(2)));
        regra("LISTA_ARG -> EXPR", (v, linha) -> mais(new ArrayList<Expressao>(), v.get(0)));

        // Expressoes
        regra("EXPR -> SIEXPR", (v, linha) -> v.get(0));
        regra("EXPR -> SIEXPR OPREL SIEXPR",
                (v, linha) -> comparar((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        for (String op : new String[]{"=", "<", ">", "<>", "<=", ">="}) {
            regra("OPREL -> " + op, (v, linha) -> v.get(0));
        }
        regra("SIEXPR -> TERMO", (v, linha) -> v.get(0));
        regra("SIEXPR -> + TERMO", (v, linha) -> sinal((Terminal) v.get(0), (Expressao) v.get(1)));
        regra("SIEXPR -> - TERMO", (v, linha) -> sinal((Terminal) v.get(0), (Expressao) v.get(1)));
        regra("SIEXPR -> SIEXPR + TERMO",
                (v, linha) -> somar((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("SIEXPR -> SIEXPR - TERMO",
                (v, linha) -> aritmetica((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("SIEXPR -> SIEXPR or TERMO",
                (v, linha) -> logica((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> FATOR", (v, linha) -> v.get(0));
        regra("TERMO -> TERMO * FATOR",
                (v, linha) -> aritmetica((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> TERMO / FATOR",
                (v, linha) -> dividir((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> TERMO and FATOR",
                (v, linha) -> logica((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> TERMO div FATOR",
                (v, linha) -> inteira((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> TERMO mod FATOR",
                (v, linha) -> inteira((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("FATOR -> numero", (v, linha) -> numero((Terminal) v.get(0)));
        regra("FATOR -> id", (v, linha) -> usarNome((Terminal) v.get(0)));
        regra("FATOR -> ( EXPR )", (v, linha) -> {
            Terminal abre = (Terminal) v.get(0);
            Expressao dentro = (Expressao) v.get(1);
            // Entre parenteses nao e mais "so uma variavel": nao serve para parametro var.
            return new Expressao(dentro.tipo, abre.getLinha(), abre.getPosicao(), null);
        });
        regra("FATOR -> constante_string", (v, linha) -> constante((Terminal) v.get(0), Tipo.STRING));
        regra("FATOR -> constante_char", (v, linha) -> constante((Terminal) v.get(0), Tipo.CHAR));
        regra("FATOR -> not FATOR", (v, linha) -> negar((Terminal) v.get(0), (Expressao) v.get(1)));
    }

    // ---------------------------------------------------------- declaracoes

    // "a, b, c: integer": declara cada nome da lista com o tipo.
    private Object declararVariaveis(Object listaDeNomes, Object terminalTipo) {
        Tipo tipo = Tipo.declarado(((Terminal) terminalTipo).getLexema());
        for (Terminal nome : terminais(listaDeNomes)) {
            declarar(nome, new Simbolo(nome.getLexema(), CategoriaSimbolo.VARIAVEL, tipo, nome.getLinha()));
        }
        return null;
    }

    // Parametros de um grupo ("var a, b: real"); so sao declarados no CAB_PROC.
    private Object parametros(Object listaDeNomes, Object terminalTipo, boolean porReferencia) {
        Tipo tipo = Tipo.declarado(((Terminal) terminalTipo).getLexema());
        List<ParametroDeclarado> lista = new ArrayList<>();
        for (Terminal nome : terminais(listaDeNomes)) {
            lista.add(new ParametroDeclarado(nome, tipo, porReferencia));
        }
        return lista;
    }

    // CAB_PROC: declara o procedimento no escopo de fora, abre o escopo dele e
    // declara os parametros ali (as variaveis locais vem depois, no BLOCO).
    private Simbolo declararProcedimento(Terminal nome, Object parametrosDeclarados) {
        Simbolo procedimento = new Simbolo(nome.getLexema(), CategoriaSimbolo.PROCEDIMENTO, null, nome.getLinha());
        declarar(nome, procedimento);
        tabela.abrirEscopo(nome.getLexema());
        for (ParametroDeclarado p : parametrosDeclarados(parametrosDeclarados)) {
            Simbolo parametro = new Simbolo(p.nome.getLexema(),
                    p.porReferencia ? CategoriaSimbolo.PARAMETRO_VAR : CategoriaSimbolo.PARAMETRO,
                    p.tipo, p.nome.getLinha());
            // Mesmo repetido, o parametro conta na posicao dele (a chamada passa um argumento para cada um).
            procedimento.adicionarParametro(parametro);
            declarar(p.nome, parametro);
        }
        return procedimento;
    }

    // Declara no escopo atual; nome repetido no mesmo escopo e erro.
    private void declarar(Terminal nome, Simbolo simbolo) {
        Simbolo existente = tabela.declarar(simbolo);
        if (existente != null) {
            erro(nome, "'" + nome.getLexema() + "' já foi declarado neste escopo ("
                    + existente.getCategoria() + ", linha " + existente.getLinha() + ")");
        }
    }

    // ------------------------------------------------------- nomes e comandos

    /*
     * O simbolo visivel com o nome do terminal. Se nao houver, relata o erro e
     * registra o nome como NAO_DECLARADO no escopo atual, para nao repetir a
     * mensagem nos proximos usos.
     */
    private Simbolo resolver(Terminal nome) {
        Simbolo s = tabela.buscar(nome.getLexema());
        if (s == null) {
            erro(nome, "Identificador '" + nome.getLexema() + "' não declarado");
            s = new Simbolo(nome.getLexema(), CategoriaSimbolo.NAO_DECLARADO, Tipo.ERRO, nome.getLinha());
            tabela.declarar(s);
        }
        return s;
    }

    // FATOR -> id: so variaveis e parametros tem valor.
    private Expressao usarNome(Terminal nome) {
        Simbolo s = resolver(nome);
        if (s.getCategoria().isVariavel()) {
            return new Expressao(s.getTipo(), nome.getLinha(), nome.getPosicao(), s);
        }
        if (s.getCategoria() != CategoriaSimbolo.NAO_DECLARADO) {
            erro(nome, "'" + nome.getLexema() + "' " + descrever(s) + " e não pode ser usado em uma expressão");
        }
        return new Expressao(Tipo.ERRO, nome.getLinha(), nome.getPosicao(), null);
    }

    // id := EXPR: o nome tem de ser variavel ou parametro, e o tipo tem de caber.
    private void atribuir(Terminal nome, Expressao valor) {
        Simbolo s = resolver(nome);
        // o erro ja foi relatado em resolver
        if (s.getCategoria() == CategoriaSimbolo.NAO_DECLARADO) {
            return;
        }
        if (!s.getCategoria().isVariavel()) {
            erro(nome, "Não é possível atribuir um valor a '" + nome.getLexema() + "', que " + descrever(s));
            return;
        }
        if (ehControleDoFor(s)) {
            erro(nome, alteraControle(nome.getLexema()));
            return;
        }
        if (valor.tipo != Tipo.ERRO && !valor.tipo.podeSerAtribuidoA(s.getTipo())) {
            erro(nome, "Tipos incompatíveis na atribuição a '" + nome.getLexema() + "': a variável é "
                    + s.getTipo() + " e a expressão é " + valor.tipo);
        }
    }

    // ------------------------------------------------------------------- for

    /*
     * for id := inicio to fim (ou downto). Como no Pascal, a variavel de
     * controle e de um tipo ordinal (aqui, integer ou char), o inicio e o fim
     * sao do mesmo tipo dela, e ela nao pode ser alterada dentro do laco (nem
     * ser a variavel de controle de um for de dentro). Devolve a variavel, que
     * fica na lista dos controles ate o fim do laco (null se nao for uma).
     */
    private Object cabecalhoDoFor(List<Object> v) {
        Terminal nome = (Terminal) v.get(1);
        Expressao inicio = (Expressao) v.get(3);
        Expressao fim = (Expressao) v.get(5);
        Simbolo s = resolver(nome);
        Simbolo controle = null;
        if (s.getCategoria() == CategoriaSimbolo.NAO_DECLARADO) {
            // o erro ja foi relatado em resolver
        } else if (!s.getCategoria().isVariavel()) {
            erro(nome, "'" + nome.getLexema() + "' " + descrever(s)
                    + " e não pode ser a variável de controle do 'for'");
        } else if (ehControleDoFor(s)) {
            erro(nome, alteraControle(nome.getLexema()));
        } else if (s.getTipo() != Tipo.INTEGER && s.getTipo() != Tipo.CHAR) {
            erro(nome, "A variável de controle do 'for' deve ser integer ou char, mas '" + nome.getLexema()
                    + "' é " + s.getTipo());
        } else {
            controle = s;
            limiteDoFor("inicial", inicio, s);
            limiteDoFor("final", fim, s);
        }
        controlesDoFor.push(controle == null ? SEM_CONTROLE : controle);
        return controle;
    }

    // O valor inicial ou final do for tem de ser do tipo da variavel de controle.
    private void limiteDoFor(String qual, Expressao limite, Simbolo controle) {
        if (limite.tipo != Tipo.ERRO && limite.tipo != controle.getTipo()) {
            erro(limite, "O valor " + qual + " do 'for' deve ser " + controle.getTipo() + ", como a variável '"
                    + controle.getNome() + "', mas é " + limite.tipo);
        }
    }

    // Fim do corpo do for: a variavel de controle volta a poder ser alterada.
    private Object fimDoFor() {
        controlesDoFor.pop();
        return null;
    }

    // Lugar na pilha dos controles de um for cujo cabecalho teve erro.
    private static final Simbolo SEM_CONTROLE =
            new Simbolo("", CategoriaSimbolo.NAO_DECLARADO, Tipo.ERRO, 0);

    // A variavel e a de controle de algum for em andamento? (o mesmo simbolo, nao so o mesmo nome)
    private boolean ehControleDoFor(Simbolo s) {
        for (Simbolo c : controlesDoFor) {
            if (c == s) {
                return true;
            }
        }
        return false;
    }

    private static String alteraControle(String nome) {
        return "'" + nome + "' é a variável de controle de um 'for' e não pode ser alterada dentro dele";
    }

    // Chamada ("p" ou "p(a, b)"): confere o que o nome e e os argumentos.
    private void chamar(Terminal nome, List<Expressao> argumentos) {
        Simbolo s = resolver(nome);
        switch (s.getCategoria()) {
            case NAO_DECLARADO:
                return;
            case PROCEDIMENTO_PREDEFINIDO:
                chamarPredefinido(nome, s, argumentos);
                return;
            case PROCEDIMENTO:
                conferirArgumentos(nome, s, argumentos);
                return;
            default:
                erro(nome, "'" + nome.getLexema() + "' " + descrever(s) + " e não pode ser chamado como procedimento");
        }
    }

    // Mesmo numero de argumentos e de parametros; depois, cada par argumento/parametro.
    private void conferirArgumentos(Terminal nome, Simbolo procedimento, List<Expressao> argumentos) {
        List<Simbolo> parametros = procedimento.getParametros();
        if (argumentos.size() != parametros.size()) {
            erro(nome, "O procedimento '" + nome.getLexema() + "' tem " + quantos(parametros.size(), "parâmetro")
                    + ", mas a chamada passa " + quantos(argumentos.size(), "argumento"));
            return;
        }
        for (int i = 0; i < argumentos.size(); i++) {
            Expressao arg = argumentos.get(i);
            Simbolo par = parametros.get(i);
            // argumento com erro: a mensagem ja saiu
            if (arg.tipo == Tipo.ERRO) {
                continue;
            }
            String inicio = "Argumento " + (i + 1) + " de '" + nome.getLexema() + "': ";
            // Parametro var: o procedimento muda a variavel, entao precisa de uma
            // variavel (nao uma expressao) e do mesmo tipo (sem conversao).
            if (par.getCategoria() == CategoriaSimbolo.PARAMETRO_VAR) {
                if (arg.variavel == null) {
                    erro(arg, inicio + "o parâmetro '" + par.getNome() + "' é var e precisa receber uma variável");
                } else if (ehControleDoFor(arg.variavel)) {
                    erro(arg, inicio + alteraControle(arg.variavel.getNome())
                            + " (o parâmetro '" + par.getNome() + "' é var)");
                } else if (arg.tipo != par.getTipo()) {
                    erro(arg, inicio + "o parâmetro var '" + par.getNome() + "' é " + par.getTipo()
                            + " e a variável '" + arg.variavel.getNome() + "' é " + arg.tipo
                            + " (num parâmetro var os tipos devem ser iguais)");
                }
            } else if (!arg.tipo.podeSerAtribuidoA(par.getTipo())) {
                erro(arg, inicio + "o parâmetro '" + par.getNome() + "' é " + par.getTipo()
                        + " e o argumento é " + arg.tipo);
            }
        }
    }

    // read(variaveis...); write(expressoes...); writeln(expressoes, ou nada).
    private void chamarPredefinido(Terminal nome, Simbolo s, List<Expressao> argumentos) {
        String proc = s.getNome(); // "read", "write" ou "writeln" (o texto pode vir em outra caixa)
        if (!proc.equals("writeln") && argumentos.isEmpty()) {
            erro(nome, "'" + nome.getLexema() + "' precisa de pelo menos um argumento");
            return;
        }
        if (proc.equals("read")) {
            for (int i = 0; i < argumentos.size(); i++) {
                Expressao arg = argumentos.get(i);
                if (arg.tipo != Tipo.ERRO && arg.variavel == null) {
                    erro(arg, "Argumento " + (i + 1) + " de '" + nome.getLexema() + "' precisa ser uma variável");
                } else if (arg.variavel != null && ehControleDoFor(arg.variavel)) {
                    erro(arg, alteraControle(arg.variavel.getNome()));
                }
            }
        }
    }

    // Conferida quando 'then' ou 'do' e empilhado.
    private void verificarCondicao(Expressao condicao, String comando) {
        if (condicao.tipo != Tipo.ERRO && condicao.tipo != Tipo.LOGICO) {
            erro(condicao, "A condição do '" + comando + "' deve ser lógica (uma comparação), mas é " + condicao.tipo);
        }
    }

    // ------------------------------------------------------------ expressoes

    // Constante numerica: integer ou real, conforme o lexico classificou.
    private Expressao numero(Terminal numero) {
        boolean inteiro = numero.getToken().getTipo() == TipoToken.NUMERO_INTEIRO;
        return new Expressao(inteiro ? Tipo.INTEGER : Tipo.REAL, numero.getLinha(), numero.getPosicao(), null);
    }

    private Expressao constante(Terminal constante, Tipo tipo) {
        return new Expressao(tipo, constante.getLinha(), constante.getPosicao(), null);
    }

    // + e - unarios
    private Expressao sinal(Terminal op, Expressao e) {
        Expressao resultado = new Expressao(e.tipo, op.getLinha(), op.getPosicao(), null);
        if (e.tipo == Tipo.ERRO || e.tipo.isNumerico()) {
            return resultado;
        }
        erro(op, "O sinal '" + op.getLexema() + "' não se aplica ao tipo " + e.tipo);
        return resultado.comTipo(Tipo.ERRO);
    }

    // '+': numeros, ou textos (char e string, dando string).
    private Expressao somar(Expressao a, Terminal op, Expressao b) {
        if (a.tipo == Tipo.ERRO || b.tipo == Tipo.ERRO) {
            return a.comTipo(Tipo.ERRO);
        }
        if (a.tipo.isTexto() && b.tipo.isTexto()) {
            return a.comTipo(Tipo.STRING);
        }
        return aritmetica(a, op, b);
    }

    // '-', '*' (e '+' entre numeros): integer se os dois forem integer, senao real.
    private Expressao aritmetica(Expressao a, Terminal op, Expressao b) {
        if (a.tipo == Tipo.ERRO || b.tipo == Tipo.ERRO) {
            return a.comTipo(Tipo.ERRO);
        }
        if (a.tipo.isNumerico() && b.tipo.isNumerico()) {
            return a.comTipo(a.tipo == Tipo.INTEGER && b.tipo == Tipo.INTEGER ? Tipo.INTEGER : Tipo.REAL);
        }
        erro(op, "O operador '" + op.getLexema() + "' não se aplica aos tipos " + a.tipo + " e " + b.tipo);
        return a.comTipo(Tipo.ERRO);
    }

    // '/': divisao de numeros, sempre real (como no Pascal: 4 / 2 da 2.0).
    private Expressao dividir(Expressao a, Terminal op, Expressao b) {
        Expressao resultado = aritmetica(a, op, b);
        return resultado.tipo == Tipo.ERRO ? resultado : resultado.comTipo(Tipo.REAL);
    }

    // and e or: os dois lados logicos (comparacoes); o resultado e logico.
    private Expressao logica(Expressao a, Terminal op, Expressao b) {
        if (a.tipo == Tipo.ERRO || b.tipo == Tipo.ERRO) {
            return a.comTipo(Tipo.ERRO);
        }
        if (a.tipo == Tipo.LOGICO && b.tipo == Tipo.LOGICO) {
            return a.comTipo(Tipo.LOGICO);
        }
        erro(op, "O operador '" + op.getLexema() + "' exige operandos lógicos (comparações), mas recebeu "
                + a.tipo + " e " + b.tipo);
        return a.comTipo(Tipo.ERRO);
    }

    // not: o operando logico (uma comparacao entre parenteses); o resultado e logico.
    private Expressao negar(Terminal op, Expressao e) {
        Expressao resultado = new Expressao(e.tipo, op.getLinha(), op.getPosicao(), null);
        if (e.tipo == Tipo.ERRO || e.tipo == Tipo.LOGICO) {
            return resultado;
        }
        erro(op, "O operador '" + op.getLexema() + "' exige um operando lógico (uma comparação), mas recebeu "
                + e.tipo);
        return resultado.comTipo(Tipo.ERRO);
    }

    // div e mod: so integer.
    private Expressao inteira(Expressao a, Terminal op, Expressao b) {
        if (a.tipo == Tipo.ERRO || b.tipo == Tipo.ERRO) {
            return a.comTipo(Tipo.ERRO);
        }
        if (a.tipo == Tipo.INTEGER && b.tipo == Tipo.INTEGER) {
            return a.comTipo(Tipo.INTEGER);
        }
        erro(op, "O operador '" + op.getLexema() + "' exige operandos integer, mas recebeu " + a.tipo + " e " + b.tipo);
        return a.comTipo(Tipo.ERRO);
    }

    // = < > <> <= >=: numeros entre si, textos entre si, logicos entre si. Resultado logico.
    private Expressao comparar(Expressao a, Terminal op, Expressao b) {
        if (a.tipo == Tipo.ERRO || b.tipo == Tipo.ERRO) {
            return a.comTipo(Tipo.ERRO);
        }
        boolean compativeis = (a.tipo.isNumerico() && b.tipo.isNumerico())
                || (a.tipo.isTexto() && b.tipo.isTexto())
                || (a.tipo == Tipo.LOGICO && b.tipo == Tipo.LOGICO);
        if (compativeis) {
            return a.comTipo(Tipo.LOGICO);
        }
        erro(op, "Comparação entre tipos incompatíveis: " + a.tipo + " e " + b.tipo);
        return a.comTipo(Tipo.ERRO);
    }

    // ----------------------------------------------------------------- apoio

    // "e um procedimento", "e uma variavel do tipo integer", "e o nome do programa"...
    private static String descrever(Simbolo s) {
        switch (s.getCategoria()) {
            case PROGRAMA:
                return "é o nome do programa";
            case VARIAVEL:
                return "é uma variável do tipo " + s.getTipo();
            case PARAMETRO:
            case PARAMETRO_VAR:
                return "é um " + s.getCategoria() + " do tipo " + s.getTipo();
            default:
                return "é um " + s.getCategoria();
        }
    }

    private static String quantos(int n, String palavra) {
        return n + " " + palavra + (n == 1 ? "" : "s");
    }

    private void erro(Terminal onde, String mensagem) {
        erros.add(new ErroCompilacao(CategoriaErro.SEMANTICO, onde.getLinha(), mensagem, onde.getPosicao()));
    }

    private void erro(Expressao onde, String mensagem) {
        erros.add(new ErroCompilacao(CategoriaErro.SEMANTICO, onde.linha, mensagem, onde.posicao));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> mais(Object lista, Object item) {
        List<T> nova = new ArrayList<>((List<T>) lista);
        nova.add((T) item);
        return nova;
    }

    @SuppressWarnings("unchecked")
    private static List<ParametroDeclarado> juntar(Object a, Object b) {
        List<ParametroDeclarado> nova = new ArrayList<>((List<ParametroDeclarado>) a);
        nova.addAll((List<ParametroDeclarado>) b);
        return nova;
    }

    @SuppressWarnings("unchecked")
    private static List<Terminal> terminais(Object lista) {
        return (List<Terminal>) lista;
    }

    @SuppressWarnings("unchecked")
    private static List<ParametroDeclarado> parametrosDeclarados(Object lista) {
        return (List<ParametroDeclarado>) lista;
    }

    @SuppressWarnings("unchecked")
    private static List<Expressao> argumentos(Object lista) {
        return (List<Expressao>) lista;
    }
}
