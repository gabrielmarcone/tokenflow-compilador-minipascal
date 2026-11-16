package minipascal.cli;

import minipascal.io.LeitorArquivoFonte;
import minipascal.semantico.Compilador;
import minipascal.semantico.ResultadoCompilacao;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Linha de comando da análise completa (léxica, sintática e semântica):
 *
 *   java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica [--tabela] <programa.txt> [saida.txt]
 *
 * Imprime "OK" se o programa estiver correto, ou o conjunto dos erros, um por
 * linha, com o tipo (léxico, sintático ou semântico) e a linha de ocorrência,
 * como pede o enunciado da Unidade II. Com --tabela, imprime em seguida a
 * tabela de símbolos. Com [saida.txt], grava o mesmo texto em UTF-8. No
 * console, os acentos são removidos (ver Console).
 *
 * Código de saída: 0 quando o resultado é "OK"; 1 quando há erros no programa
 * ou falha de leitura/gravação.
 */
public final class AnaliseSemantica {

    static final String OPCAO_TABELA = "--tabela";

    private static final String USO =
            "Uso: java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica [--tabela] <programa.txt> [saida.txt]";

    private AnaliseSemantica() {
    }

    public static void main(String[] args) {
        System.exit(executar(args));
    }

    public static int executar(String[] args) {
        boolean mostrarTabela = false;
        List<String> arquivos = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals(OPCAO_TABELA)) {
                mostrarTabela = true;
            } else {
                arquivos.add(arg);
            }
        }
        if (arquivos.size() < 1 || arquivos.size() > 2) {
            System.out.println(USO);
            return 1;
        }
        String entrada = arquivos.get(0);
        if (arquivos.size() == 2
                && new File(arquivos.get(1)).getAbsoluteFile().equals(new File(entrada).getAbsoluteFile())) {
            System.out.println("O arquivo de saida nao pode ser o mesmo da entrada.");
            return 1;
        }
        try {
            String fonte = LeitorArquivoFonte.ler(entrada);
            ResultadoCompilacao resultado = new Compilador().analisar(fonte);
            String texto = resultado.formatar();
            if (mostrarTabela) {
                texto += "\n\n" + textoDaTabela(resultado);
            }
            System.out.println(Console.semAcentos(texto));
            if (arquivos.size() == 2) {
                Files.write(Paths.get(arquivos.get(1)), (texto + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return resultado.isSucesso() ? 0 : 1;
        } catch (NoSuchFileException e) {
            System.out.println("Arquivo nao encontrado: " + entrada);
            return 1;
        } catch (IOException e) {
            System.out.println("Erro ao processar o arquivo: " + Console.semAcentos(e.getMessage()));
            return 1;
        } catch (InvalidPathException e) {
            System.out.println("Caminho invalido: " + Console.semAcentos(e.getMessage()));
            return 1;
        }
    }

    static String textoDaTabela(ResultadoCompilacao resultado) {
        String titulo = "Tabela de símbolos";
        if (!resultado.isSemanticaCompleta()) {
            titulo += " (a análise semântica parou no primeiro erro sintático: só o que foi declarado até ali)";
        }
        String tabela = resultado.getTabelaSimbolos().listar();
        return titulo + ":\n" + tabela.substring(0, tabela.length() - 1); // sem a última quebra de linha
    }
}
