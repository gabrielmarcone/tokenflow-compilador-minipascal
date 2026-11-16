package minipascal.semantico;

import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.model.TipoToken;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.parser.AcoesReducao;
import minipascal.sintatico.parser.Terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Análise semântica do Mini_Pascal, feita durante a análise sintática: são as
 * ações executadas a cada redução do analisador SLR(1) (ver AcoesReducao).
 *
 * Cada símbolo da pilha do analisador tem um atributo (o valor da pilha de
 * valores). Os terminais levam o próprio Terminal (lexema, linha, posição);
 * os não terminais levam o que a regra da sua produção montou:
 *   - LISTA_ID, LISTA_ID_PARAM: os identificadores, em ordem;
 *   - PALIST, LISTA_PARAM, PARAM: os parâmetros declarados (nome, tipo, var);
 *   - CAB_PROC: o símbolo do procedimento;
 *   - EXPR, SIEXPR, TERMO, FATOR: uma Expressao (tipo, linha e, se a
 *     expressão é só uma variável, qual é ela);
 *   - LISTA_ARG: as expressões dos argumentos;
 *   - os demais: nada (null).
 * É uma definição S-atribuída: todo atributo é calculado a partir dos filhos.
 *
 * Tabela de símbolos e escopos (ver TabelaSimbolos):
 *   - read, write e writeln são predefinidos, no escopo externo;
 *   - o nome do programa vai para o escopo externo quando é empilhado, logo
 *     depois de 'program', e então o escopo global é aberto;
 *   - as variáveis são declaradas ao reduzir DECL_VAR;
 *   - ao reduzir CAB_PROC (cabeçalho do procedimento, antes do seu bloco), o
 *     procedimento é declarado no escopo atual e o escopo dele é aberto, com os
 *     parâmetros; ao reduzir BLOCO_PROC -> BLOCO_PROC CAB_PROC ; BLOCO ;, o
 *     escopo é fechado. Como o procedimento é declarado antes do bloco, ele
 *     pode chamar a si mesmo (recursão).
 *
 * Verificações (cada uma com a mensagem que gera, em ErroCompilacao com a
 * categoria SEMANTICO):
 *   - nome não declarado, ou declarado duas vezes no mesmo escopo;
 *   - procedimento ou nome do programa usado como variável, e variável
 *     chamada como procedimento;
 *   - atribuição entre tipos incompatíveis (ver Tipo.podeSerAtribuidoA);
 *   - operadores: + - * e sinal só com números ('+' também junta textos);
 *     div e mod só com integer; comparação só entre números, entre textos
 *     (char e string) ou entre lógicos;
 *   - condição do if e do while tem de ser lógica (uma comparação);
 *   - chamada: número de argumentos igual ao de parâmetros; argumento de
 *     parâmetro por valor atribuível ao tipo do parâmetro; argumento de
 *     parâmetro var tem de ser uma variável do mesmo tipo;
 *   - read precisa de variáveis; read e write, de pelo menos um argumento;
 *     writeln aceita qualquer quantidade.
 *
 * Para não relatar cascatas: uma expressão com erro tem o tipo ERRO, que não
 * gera novas mensagens; um nome não declarado é registrado depois da primeira
 * mensagem, para que os usos seguintes não a repitam.
 *
 * Depois do primeiro erro sintático, a análise semântica para (os erros
 * semânticos encontrados até ali continuam valendo). A recuperação de erros
 * pode descartar declarações e inventar construções, e verificar o que vem
 * depois produziria erros semânticos que não existem no programa.
 */
public final class AnalisadorSemantico implements AcoesReducao {

    // ------------------------------------------------------------ atributos

    // Atributo de EXPR, SIEXPR, TERMO e FATOR.
    private static final class Expressao {
        final Tipo tipo;
        final int linha;
        final int posicao;
        // A variável, quando a expressão é só ela (pode ir para um parâmetro var ou para read).
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

    // Atributo de PARAM, LISTA_PARAM e PALIST: um parâmetro como foi declarado.
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

    // A ação de uma produção: recebe os atributos do corpo e devolve o da cabeça.
    private interface Regra {
        Object aplicar(List<Object> v, int linha);
    }

    // --------------------------------------------------------------- estado

    private final TabelaSimbolos tabela = new TabelaSimbolos();
    private final Map<String, Regra> regras = new HashMap<>();
    private final List<ErroCompilacao> erros = new ArrayList<>();
    private boolean interrompida = false;

    // Último terminal empilhado (para reconhecer o nome do programa, logo depois de 'program').
    private Terminal anterior;
    // Última EXPR reduzida: ao empilhar 'then' ou 'do', é a da condição.
    private Expressao ultimaExpressao;

    /**
     * @param gramatica a gramática do analisador sintático; toda produção dela
     *                  precisa ter uma regra aqui, e toda regra, uma produção
     *                  (se não, IllegalArgumentException: a gramática mudou e a
     *                  semântica não acompanhou)
     */
    public AnalisadorSemantico(Gramatica gramatica) {
        registrarRegras();
        List<String> problemas = conferirCobertura(gramatica);
        if (!problemas.isEmpty()) {
            throw new IllegalArgumentException("a análise semântica não corresponde à gramática: " + problemas);
        }
        for (String nome : new String[]{"read", "write", "writeln"}) {
            tabela.declarar(new Simbolo(nome, CategoriaSimbolo.PROCEDIMENTO_PREDEFINIDO, null, 0));
        }
    }

    // Produções sem regra e regras sem produção (vazio quando correspondem).
    List<String> conferirCobertura(Gramatica gramatica) {
        TreeSet<String> semRegra = new TreeSet<>();
        TreeSet<String> semProducao = new TreeSet<>(regras.keySet());
        for (Producao p : gramatica.getProducoes()) {
            if (p.getNumero() == 0) {
                continue; // S' -> PROG: nunca é reduzida (é a aceitação)
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

    // Todos os erros semânticos encontrados, na ordem em que foram encontrados.
    @Override
    public List<ErroCompilacao> getErros() {
        return Collections.unmodifiableList(erros);
    }

    // Verdadeiro se a análise parou num erro sintático (ver o comentário da classe).
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
            // O nome do programa fica no escopo externo: não conflita com os nomes
            // do programa (como no Pascal ISO) e não pode ser usado como variável.
            // Se coincidir com read, write ou writeln, fica só o predefinido.
            tabela.declarar(new Simbolo(terminal.getLexema(), CategoriaSimbolo.PROGRAMA, null, terminal.getLinha()));
            tabela.abrirEscopo(TabelaSimbolos.ESCOPO_GLOBAL);
        }
        if (terminal.getNome().equals("then") || terminal.getNome().equals("do")) {
            // 'then' e 'do' só aparecem depois da condição (if EXPR then, while EXPR do).
            // Num analisador LR, o 'then' só é empilhado depois de a condição ser
            // reduzida a EXPR, e essa é a última redução antes dele: a condição é a
            // última EXPR reduzida. Conferir aqui, e não na redução do if inteiro,
            // faz o erro da condição sair antes dos erros do corpo.
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
        Regra regra = regras.get(producao.toString());
        Object valor = regra.aplicar(valores, linha);
        if (producao.getCabeca().equals("EXPR")) {
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

    // Produções sem efeito semântico (estrutura de blocos e comandos).
    private void semAcao(String... producoes) {
        for (String p : producoes) {
            regra(p, (v, linha) -> null);
        }
    }

    private void registrarRegras() {
        // Programa, blocos e listas de comandos
        semAcao("PROG -> program id ; BLOCO .",
                "BLOCO -> BLOCO_VAR BLOCO_PROC begin LISTA_CMD end",
                "BLOCO_VAR -> BLOCO_VAR var DECL_VAR",
                "BLOCO_VAR -> ε",
                "BLOCO_PROC -> ε",
                "LISTA_CMD -> LISTA_CMD COMANDO ;",
                "LISTA_CMD -> COMANDO ;");

        // Declarações de variáveis
        regra("DECL_VAR -> DECL_VAR LISTA_ID : tipo ;", (v, linha) -> declararVariaveis(v.get(1), v.get(3)));
        regra("DECL_VAR -> LISTA_ID : tipo ;", (v, linha) -> declararVariaveis(v.get(0), v.get(2)));
        regra("LISTA_ID -> LISTA_ID , id", (v, linha) -> mais(v.get(0), v.get(2)));
        regra("LISTA_ID -> id", (v, linha) -> mais(new ArrayList<Terminal>(), v.get(0)));

        // Procedimentos e parâmetros
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

        // Comandos (a condição de if e while é conferida em aoEmpilhar, no 'then' e no 'do')
        semAcao("COMANDO -> CMD_FECHADO",
                "COMANDO -> CMD_ABERTO",
                "CMD_FECHADO -> SIMPLES",
                "CMD_FECHADO -> if EXPR then CMD_FECHADO else CMD_FECHADO",
                "CMD_FECHADO -> while EXPR do CMD_FECHADO",
                "CMD_ABERTO -> if EXPR then COMANDO",
                "CMD_ABERTO -> if EXPR then CMD_FECHADO else CMD_ABERTO",
                "CMD_ABERTO -> while EXPR do CMD_ABERTO",
                "SIMPLES -> begin LISTA_CMD end");
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

        // Expressões
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
        regra("TERMO -> FATOR", (v, linha) -> v.get(0));
        regra("TERMO -> TERMO * FATOR",
                (v, linha) -> aritmetica((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> TERMO div FATOR",
                (v, linha) -> inteira((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("TERMO -> TERMO mod FATOR",
                (v, linha) -> inteira((Expressao) v.get(0), (Terminal) v.get(1), (Expressao) v.get(2)));
        regra("FATOR -> numero", (v, linha) -> numero((Terminal) v.get(0)));
        regra("FATOR -> id", (v, linha) -> usarNome((Terminal) v.get(0)));
        regra("FATOR -> ( EXPR )", (v, linha) -> {
            Terminal abre = (Terminal) v.get(0);
            Expressao dentro = (Expressao) v.get(1);
            // Entre parênteses não é mais "só uma variável": não serve para parâmetro var.
            return new Expressao(dentro.tipo, abre.getLinha(), abre.getPosicao(), null);
        });
        regra("FATOR -> constante_string", (v, linha) -> constante((Terminal) v.get(0), Tipo.STRING));
        regra("FATOR -> constante_char", (v, linha) -> constante((Terminal) v.get(0), Tipo.CHAR));
    }

    // ---------------------------------------------------------- declarações

    private Object declararVariaveis(Object listaDeNomes, Object terminalTipo) {
        Tipo tipo = Tipo.declarado(((Terminal) terminalTipo).getLexema());
        for (Terminal nome : terminais(listaDeNomes)) {
            declarar(nome, new Simbolo(nome.getLexema(), CategoriaSimbolo.VARIAVEL, tipo, nome.getLinha()));
        }
        return null;
    }

    private Object parametros(Object listaDeNomes, Object terminalTipo, boolean porReferencia) {
        Tipo tipo = Tipo.declarado(((Terminal) terminalTipo).getLexema());
        List<ParametroDeclarado> lista = new ArrayList<>();
        for (Terminal nome : terminais(listaDeNomes)) {
            lista.add(new ParametroDeclarado(nome, tipo, porReferencia));
        }
        return lista;
    }

    private Simbolo declararProcedimento(Terminal nome, Object parametrosDeclarados) {
        Simbolo procedimento = new Simbolo(nome.getLexema(), CategoriaSimbolo.PROCEDIMENTO, null, nome.getLinha());
        declarar(nome, procedimento);
        tabela.abrirEscopo(nome.getLexema());
        for (ParametroDeclarado p : parametrosDeclarados(parametrosDeclarados)) {
            Simbolo parametro = new Simbolo(p.nome.getLexema(),
                    p.porReferencia ? CategoriaSimbolo.PARAMETRO_VAR : CategoriaSimbolo.PARAMETRO,
                    p.tipo, p.nome.getLinha());
            // Mesmo repetido, o parâmetro conta na posição dele (a chamada passa um argumento para cada um).
            procedimento.adicionarParametro(parametro);
            declarar(p.nome, parametro);
        }
        return procedimento;
    }

    private void declarar(Terminal nome, Simbolo simbolo) {
        Simbolo existente = tabela.declarar(simbolo);
        if (existente != null) {
            erro(nome, "'" + nome.getLexema() + "' já foi declarado neste escopo ("
                    + existente.getCategoria() + ", linha " + existente.getLinha() + ")");
        }
    }

    // ------------------------------------------------------- nomes e comandos

    /*
     * O símbolo visível com o nome do terminal. Se não houver, relata o erro e
     * registra o nome como NAO_DECLARADO no escopo atual, para não repetir a
     * mensagem nos próximos usos.
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

    // FATOR -> id: só variáveis e parâmetros têm valor.
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

    private void atribuir(Terminal nome, Expressao valor) {
        Simbolo s = resolver(nome);
        if (s.getCategoria() == CategoriaSimbolo.NAO_DECLARADO) {
            return;
        }
        if (!s.getCategoria().isVariavel()) {
            erro(nome, "Não é possível atribuir um valor a '" + nome.getLexema() + "', que " + descrever(s));
            return;
        }
        if (valor.tipo != Tipo.ERRO && !valor.tipo.podeSerAtribuidoA(s.getTipo())) {
            erro(nome, "Tipos incompatíveis na atribuição a '" + nome.getLexema() + "': a variável é "
                    + s.getTipo() + " e a expressão é " + valor.tipo);
        }
    }

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
            if (arg.tipo == Tipo.ERRO) {
                continue;
            }
            String inicio = "Argumento " + (i + 1) + " de '" + nome.getLexema() + "': ";
            if (par.getCategoria() == CategoriaSimbolo.PARAMETRO_VAR) {
                if (arg.variavel == null) {
                    erro(arg, inicio + "o parâmetro '" + par.getNome() + "' é var e precisa receber uma variável");
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

    // read(variáveis...); write(expressões...); writeln(expressões, ou nada).
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
                }
            }
        }
    }

    // Conferida quando 'then' ou 'do' é empilhado.
    private void verificarCondicao(Expressao condicao, String comando) {
        if (condicao.tipo != Tipo.ERRO && condicao.tipo != Tipo.LOGICO) {
            erro(condicao, "A condição do '" + comando + "' deve ser lógica (uma comparação), mas é " + condicao.tipo);
        }
    }

    // ------------------------------------------------------------ expressões

    private Expressao numero(Terminal numero) {
        boolean inteiro = numero.getToken().getTipo() == TipoToken.NUMERO_INTEIRO;
        return new Expressao(inteiro ? Tipo.INTEGER : Tipo.REAL, numero.getLinha(), numero.getPosicao(), null);
    }

    private Expressao constante(Terminal constante, Tipo tipo) {
        return new Expressao(tipo, constante.getLinha(), constante.getPosicao(), null);
    }

    // + e - unários
    private Expressao sinal(Terminal op, Expressao e) {
        Expressao resultado = new Expressao(e.tipo, op.getLinha(), op.getPosicao(), null);
        if (e.tipo == Tipo.ERRO || e.tipo.isNumerico()) {
            return resultado;
        }
        erro(op, "O sinal '" + op.getLexema() + "' não se aplica ao tipo " + e.tipo);
        return resultado.comTipo(Tipo.ERRO);
    }

    // '+': números, ou textos (char e string, dando string).
    private Expressao somar(Expressao a, Terminal op, Expressao b) {
        if (a.tipo == Tipo.ERRO || b.tipo == Tipo.ERRO) {
            return a.comTipo(Tipo.ERRO);
        }
        if (a.tipo.isTexto() && b.tipo.isTexto()) {
            return a.comTipo(Tipo.STRING);
        }
        return aritmetica(a, op, b);
    }

    // '-', '*' (e '+' entre números): integer se os dois forem integer, senão real.
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

    // div e mod: só integer.
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

    // = < > <> <= >=: números entre si, textos entre si, lógicos entre si. Resultado lógico.
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

    // "é um procedimento", "é uma variável do tipo integer", "é o nome do programa"...
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
