package minipascal.cli;

import minipascal.io.LeitorArquivoFonte;
import minipascal.sintatico.parser.AnalisadorSintatico;
import minipascal.sintatico.parser.ResultadoAnalise;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

/**
 * Linha de comando do analisador sintático:
 *
 *   java -cp TokenFlow.jar minipascal.cli.AnaliseSintatica <programa.txt> [saida.txt]
 *
 * Imprime "OK" se o programa estiver correto, ou o conjunto dos erros, um por
 * linha, com o tipo e a linha de ocorrência (como pede o enunciado da Unidade
 * II): a análise se recupera de cada erro sintático e continua até o fim do
 * arquivo (ver AnalisadorSintatico). Com
 * [saida.txt], grava o mesmo resultado em UTF-8. No console, os acentos são
 * removidos (ver Console).
 *
 * Código de saída: 0 quando o resultado é "OK"; 1 quando há erros no programa
 * ou falha de leitura/gravação.
 *
 * A gramática usada é a completa (gramatica.txt): o arquivo deve conter um
 * programa Mini_Pascal inteiro, de "program" até o "." final.
 */
public final class AnaliseSintatica {

    static final String GRAMATICA = AnalisadorSintatico.GRAMATICA_COMPLETA;

    private AnaliseSintatica() {
    }

    public static void main(String[] args) {
        System.exit(executar(args));
    }

    public static int executar(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Uso: java -cp TokenFlow.jar minipascal.cli.AnaliseSintatica <programa.txt> [saida.txt]");
            return 1;
        }
        String entrada = args[0];
        if (args.length == 2
                && new File(args[1]).getAbsoluteFile().equals(new File(entrada).getAbsoluteFile())) {
            System.out.println("O arquivo de saida nao pode ser o mesmo da entrada.");
            return 1;
        }
        try {
            String fonte = LeitorArquivoFonte.ler(entrada);
            AnalisadorSintatico analisador = new AnalisadorSintatico(AnalisadorSintatico.tabelaEmbutida(GRAMATICA));
            ResultadoAnalise resultado = analisador.analisar(fonte);
            String texto = resultado.formatar();
            System.out.println(Console.semAcentos(texto));
            if (args.length == 2) {
                Files.write(Paths.get(args[1]), (texto + "\n").getBytes(StandardCharsets.UTF_8));
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
}
