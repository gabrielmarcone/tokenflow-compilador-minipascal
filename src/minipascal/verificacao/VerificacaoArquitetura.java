package minipascal.verificacao;

import minipascal.cli.Console;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Verifica a organizacao dos modulos, lendo o codigo-fonte em src/ (rodar a
 * partir da raiz do projeto, como faz o build.bat).
 *
 * Camadas, de baixo para cima; cada modulo so pode usar os de baixo:
 *
 *   lexico                  analise lexica (nao depende de nenhum outro modulo)
 *   erros                   erros de compilacao (tipo, linha, mensagem)
 *   io                      leitura de fontes e gravacao da listagem de tokens
 *   sintatico               gramatica, gerador SLR(1) e analisador sintatico
 *   semantico               analise semantica: tabela de simbolos, tipos e as acoes de reducao
 *   cli, gui                linha de comando e interface grafica (usam todos)
 *
 * Os pacotes "verificacao" (as suites) podem usar qualquer modulo; nenhum
 * codigo de producao pode usar uma suite. Um pacote novo no topo de
 * minipascal precisa ser incluido aqui de proposito.
 */
public class VerificacaoArquitetura {

    private static int total = 0;
    private static int falhas = 0;

    private static final Path FONTES = Paths.get("src", "minipascal");

    // Modulo -> modulos que ele pode usar (alem dele mesmo).
    private static final Map<String, Set<String>> PERMITIDOS = new HashMap<>();

    static {
        permitir("lexico");
        permitir("erros");
        permitir("io", "lexico");
        permitir("sintatico", "lexico", "erros", "io");
        permitir("semantico", "lexico", "erros", "io", "sintatico");
        permitir("cli", "lexico", "erros", "io", "sintatico", "semantico");
        permitir("gui", "lexico", "erros", "io", "sintatico", "semantico");
        permitir("verificacao", "lexico", "erros", "io", "sintatico", "semantico", "cli", "gui");
    }

    private static final Pattern PACOTE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern REFERENCIA = Pattern.compile("\\bminipascal\\.(\\w+)((?:\\.\\w+)*)");

    public static void main(String[] args) {
        if (!Files.isDirectory(FONTES)) {
            checar("pasta src/minipascal encontrada (rode a partir da raiz do projeto)", false);
            terminar();
        }
        try {
            verificarFontes();
        } catch (IOException | RuntimeException e) {
            checar("leitura dos fontes: " + e, false);
        }
        verificarPontosDeEntrada();
        verificarRecursos();
        terminar();
    }

    private static void verificarFontes() throws IOException {
        List<Path> arquivos;
        try (Stream<Path> s = Files.walk(FONTES)) {
            arquivos = s.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
        checar("há fontes Java em src/minipascal (" + arquivos.size() + " arquivos)", !arquivos.isEmpty());

        Set<String> modulos = new HashSet<>();
        List<String> pacoteErrado = new ArrayList<>();
        List<String> moduloDesconhecido = new ArrayList<>();
        List<String> dependenciaProibida = new ArrayList<>();
        List<String> usaVerificacao = new ArrayList<>();

        for (Path arquivo : arquivos) {
            Path relativo = FONTES.relativize(arquivo);
            String nome = relativo.toString().replace('\\', '/');
            String modulo = relativo.getName(0).toString();
            boolean ehSuite = nome.startsWith("verificacao/") || nome.contains("/verificacao/");
            String texto = new String(Files.readAllBytes(arquivo), StandardCharsets.UTF_8);

            // package declarado = pasta do arquivo
            String pastaComoPacote = "minipascal." + (relativo.getParent() == null ? ""
                    : relativo.getParent().toString().replace('\\', '.').replace('/', '.'));
            Matcher mp = PACOTE.matcher(texto);
            if (!mp.find() || !mp.group(1).equals(pastaComoPacote)) {
                pacoteErrado.add(nome);
            }

            if (!PERMITIDOS.containsKey(modulo)) {
                moduloDesconhecido.add(nome);
                continue;
            }
            modulos.add(modulo);

            Matcher mr = REFERENCIA.matcher(semComentariosNemTextos(texto));
            while (mr.find()) {
                String alvo = mr.group(1);
                String caminho = mr.group(0);
                if (!ehSuite && (alvo.equals("verificacao") || caminho.contains(".verificacao"))) {
                    usaVerificacao.add(nome + " -> " + caminho);
                    continue;
                }
                if (!ehSuite && !alvo.equals(modulo) && !PERMITIDOS.get(modulo).contains(alvo)) {
                    dependenciaProibida.add(nome + " -> " + caminho);
                }
            }
        }

        checar("todo arquivo declara o package da sua pasta" + lista(pacoteErrado), pacoteErrado.isEmpty());
        checar("só existem os módulos previstos em minipascal" + lista(moduloDesconhecido),
                moduloDesconhecido.isEmpty());
        checar("módulos lexico, erros, io, sintatico, semantico, cli e gui presentes",
                modulos.containsAll(Arrays.asList("lexico", "erros", "io", "sintatico", "semantico", "cli", "gui")));
        checar("cada módulo só usa os módulos abaixo dele" + lista(dependenciaProibida),
                dependenciaProibida.isEmpty());
        checar("nenhum código de produção usa as suítes de verificação" + lista(usaVerificacao),
                usaVerificacao.isEmpty());

        // A interface declara seu controlador no FXML; ele precisa apontar para o pacote gui.
        Path fxml = FONTES.resolve("gui").resolve("Main.fxml");
        String conteudoFxml = Files.isRegularFile(fxml)
                ? new String(Files.readAllBytes(fxml), StandardCharsets.UTF_8) : "";
        checar("Main.fxml aponta para minipascal.gui.AnalisadorController",
                conteudoFxml.contains("fx:controller=\"minipascal.gui.AnalisadorController\""));
    }

    // As classes que o usuario executa existem e tem main publico e estatico.
    private static void verificarPontosDeEntrada() {
        for (String classe : new String[]{"minipascal.cli.AnaliseLexica", "minipascal.cli.AnaliseSintatica",
                "minipascal.cli.AnaliseSemantica", "minipascal.cli.GeradorTabelas"}) {
            checar("ponto de entrada " + classe + " com main público", temMainPublico(classe));
        }
        // A interface depende do JavaFX (embutido no JDK 8 da Oracle). Sem ele, a
        // classe nao pode ser carregada; ai confere-se ao menos que ela esta no lugar.
        if (javaFxDisponivel()) {
            checar("ponto de entrada minipascal.gui.MainApp com main público", temMainPublico("minipascal.gui.MainApp"));
        } else {
            checar("minipascal.gui.MainApp presente (JavaFX ausente neste Java: main não conferido)",
                    VerificacaoArquitetura.class.getResource("/minipascal/gui/MainApp.class") != null);
        }
    }

    private static boolean temMainPublico(String classe) {
        try {
            Method main = Class.forName(classe, false, VerificacaoArquitetura.class.getClassLoader())
                    .getMethod("main", String[].class);
            return Modifier.isStatic(main.getModifiers()) && Modifier.isPublic(main.getModifiers());
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static boolean javaFxDisponivel() {
        try {
            Class.forName("javafx.application.Application", false, VerificacaoArquitetura.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    // Os recursos que o programa carrega pelo classpath estao junto das classes (no jar ou em out/).
    private static void verificarRecursos() {
        String[][] recursos = {
                {"/minipascal/gui/Main.fxml", "tela da interface"},
                {"/minipascal/gui/application.css", "estilo da interface"},
                {"/minipascal/gui/icon.png", "ícone da janela"},
                {"/minipascal/sintatico/gramatica/gramatica.txt", "gramática completa"},
                {"/minipascal/sintatico/gramatica/expressoes.txt", "gramática das expressões"}};
        for (String[] r : recursos) {
            checar("recurso no classpath: " + r[0] + " (" + r[1] + ")",
                    VerificacaoArquitetura.class.getResource(r[0]) != null);
        }
    }

    // Tira comentarios, strings e chars, para que so o codigo conte como dependencia.
    static String semComentariosNemTextos(String texto) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = texto.length();
        while (i < n) {
            char c = texto.charAt(i);
            if (c == '/' && i + 1 < n && texto.charAt(i + 1) == '/') {
                while (i < n && texto.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && texto.charAt(i + 1) == '*') {
                int fim = texto.indexOf("*/", i + 2);
                i = fim < 0 ? n : fim + 2;
                sb.append(' ');
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < n && texto.charAt(i) != c) {
                    i += texto.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
                sb.append(c).append(c);
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static void permitir(String modulo, String... usados) {
        PERMITIDOS.put(modulo, new HashSet<>(Arrays.asList(usados)));
    }

    private static String lista(List<String> itens) {
        return itens.isEmpty() ? "" : " " + itens;
    }

    private static void terminar() {
        System.out.println();
        System.out.println(total + " verificacoes de arquitetura, " + falhas + " falha(s).");
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
