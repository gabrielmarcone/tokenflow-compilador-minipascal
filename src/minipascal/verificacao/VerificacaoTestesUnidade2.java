package minipascal.verificacao;

import minipascal.cli.AnaliseSemantica;
import minipascal.cli.Console;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Roda os arquivos de teste da Unidade II (testes-integracao/unidade2) pela
 * linha de comando da análise completa, como o usuário faria:
 *
 *   java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica --tabela programa.txt saida.txt
 *
 * e compara o arquivo gravado com o resultado esperado, em
 * testes-integracao/unidade2/esperado (os erros, com tipo, linha e mensagem,
 * ou "OK", e a tabela de símbolos). Os resultados esperados foram escritos e
 * conferidos à mão, campo por campo (ver o README da pasta).
 *
 * Também confere o código de saída (0 só para "OK"), o texto do console (o
 * mesmo, sem acentos) e que o resultado não muda se o arquivo tiver quebras
 * de linha do Windows (CRLF).
 *
 * Rodar a partir da raiz do projeto, como faz o build.bat.
 */
public final class VerificacaoTestesUnidade2 {

    private static final Path PASTA = Paths.get("testes-integracao", "unidade2");
    private static final Path ESPERADO = PASTA.resolve("esperado");
    // O enunciado pede pelo menos 5 arquivos de teste.
    private static final int MINIMO_DE_TESTES = 5;

    private static int total = 0;
    private static int falhas = 0;

    private VerificacaoTestesUnidade2() {
    }

    public static void main(String[] args) {
        if (!Files.isDirectory(PASTA) || !Files.isDirectory(ESPERADO)) {
            checar("pastas " + PASTA + " e " + ESPERADO + " encontradas (rode a partir da raiz do projeto)", false);
            terminar();
        }
        try {
            List<Path> programas = arquivosTxt(PASTA);
            List<Path> esperados = arquivosTxt(ESPERADO);
            checar("pelo menos " + MINIMO_DE_TESTES + " arquivos de teste (" + programas.size() + ")",
                    programas.size() >= MINIMO_DE_TESTES);
            boolean algumOk = false;
            boolean lexico = false;
            boolean sintatico = false;
            boolean semantico = false;
            for (Path programa : programas) {
                Path esperado = ESPERADO.resolve(programa.getFileName());
                if (!Files.exists(esperado)) {
                    checar(programa.getFileName() + ": tem resultado esperado", false);
                    continue;
                }
                String texto = normalizar(new String(Files.readAllBytes(esperado), StandardCharsets.UTF_8));
                conferir(programa, texto);
                algumOk |= texto.startsWith("OK\n");
                lexico |= texto.contains("Erro léxico na linha ");
                sintatico |= texto.contains("Erro sintático na linha ");
                semantico |= texto.contains("Erro semântico na linha ");
            }
            for (Path esperado : esperados) {
                checar("esperado/" + esperado.getFileName() + ": tem o programa de teste",
                        Files.exists(PASTA.resolve(esperado.getFileName())));
            }
            checar("os testes cobrem programas corretos e erros léxicos, sintáticos e semânticos",
                    algumOk && lexico && sintatico && semantico);
        } catch (IOException | RuntimeException e) {
            checar("execução dos testes: " + e, false);
        }
        terminar();
    }

    /*
     * Roda o programa pela linha de comando e compara arquivo, console e
     * código de saída com o esperado; depois repete com o arquivo em CRLF.
     */
    private static void conferir(Path programa, String esperado) throws IOException {
        String nome = programa.getFileName().toString();
        boolean ok = esperado.startsWith("OK\n");
        Execucao e = executar(programa);
        checar(nome + ": saída igual à esperada" + diferenca(e.arquivo, esperado), e.arquivo.equals(esperado));
        checar(nome + ": código de saída " + (ok ? 0 : 1), e.codigo == (ok ? 0 : 1));
        checar(nome + ": console com o mesmo texto, sem acentos",
                e.console.equals(Console.semAcentos(esperado)));

        Path crlf = Files.createTempFile("tokenflow-crlf", ".txt");
        try {
            String fonte = new String(Files.readAllBytes(programa), StandardCharsets.UTF_8);
            String comCrlf = normalizar(fonte).replace("\n", "\r\n");
            Files.write(crlf, comCrlf.getBytes(StandardCharsets.UTF_8));
            Execucao windows = executar(crlf);
            checar(nome + ": mesmo resultado com quebras de linha CRLF", windows.arquivo.equals(esperado));
        } finally {
            Files.deleteIfExists(crlf);
        }
    }

    private static final class Execucao {
        String arquivo;
        String console;
        int codigo;
    }

    private static Execucao executar(Path programa) throws IOException {
        Path saida = Files.createTempFile("tokenflow-saida", ".txt");
        PrintStream original = System.out;
        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        Execucao e = new Execucao();
        try {
            System.setOut(new PrintStream(capturado, true, "UTF-8"));
            e.codigo = AnaliseSemantica.executar(new String[]{"--tabela", programa.toString(), saida.toString()});
        } finally {
            System.setOut(original);
        }
        try {
            e.arquivo = normalizar(new String(Files.readAllBytes(saida), StandardCharsets.UTF_8));
            e.console = normalizar(new String(capturado.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(saida);
        }
        return e;
    }

    // Primeira linha diferente, para a mensagem de falha.
    private static String diferenca(String real, String esperado) {
        if (real.equals(esperado)) {
            return "";
        }
        String[] a = real.split("\n", -1);
        String[] b = esperado.split("\n", -1);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "(fim)";
            String y = i < b.length ? b[i] : "(fim)";
            if (!x.equals(y)) {
                return " (linha " + (i + 1) + ": veio \"" + Console.ascii(x) + "\", esperado \""
                        + Console.ascii(y) + "\")";
            }
        }
        return "";
    }

    // Os arquivos podem ter vindo do git com CRLF no Windows.
    private static String normalizar(String texto) {
        return texto.replace("\r\n", "\n");
    }

    private static List<Path> arquivosTxt(Path pasta) throws IOException {
        List<Path> lista = new ArrayList<>();
        try (DirectoryStream<Path> d = Files.newDirectoryStream(pasta, "*.txt")) {
            for (Path p : d) {
                lista.add(p);
            }
        }
        Collections.sort(lista);
        return lista;
    }

    private static void terminar() {
        System.out.println();
        System.out.println(total + " verificacoes dos testes da Unidade II, " + falhas + " falha(s).");
        System.exit(falhas > 0 ? 1 : 0);
    }

    private static void checar(String descricao, boolean condicao) {
        total++;
        String texto = Console.ascii(descricao);
        if (condicao) {
            System.out.println("[OK]    " + texto);
        } else {
            falhas++;
            System.out.println("[FALHA] " + texto);
        }
    }
}
