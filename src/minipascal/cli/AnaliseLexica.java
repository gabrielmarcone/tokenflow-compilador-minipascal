package minipascal.cli;

import minipascal.lexico.core.AnalisadorLexico;
import minipascal.io.EscritorArquivoSaida;
import minipascal.io.LeitorArquivoFonte;
import minipascal.lexico.model.Token;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

/**
 * Linha de comando da analise lexica (a saida da Unidade I):
 *
 *   java -cp TokenFlow.jar minipascal.cli.AnaliseLexica <entrada.txt> [saida.txt]
 *
 * Grava um par lexema/classe por linha, em UTF-8.
 */
public class AnaliseLexica {

    // Modo linha de comando: le o arquivo, chama o analisador ate ele devolver
    // null e grava um par lexema/token por linha. Erros lexicos entram na saida
    // como tokens; so falha de leitura ou gravacao interrompe a execucao.
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java -cp TokenFlow.jar minipascal.cli.AnaliseLexica <entrada.txt> [saida.txt]");
            return;
        }

        String caminhoEntrada = args[0];
        String caminhoSaida = args.length >= 2 ? args[1] : gerarNomeSaida(caminhoEntrada);

        // Entrada que e pasta, saida igual a entrada (apagaria o programa),
        // pasta de saida que nao existe.
        String problema = Caminhos.problema(caminhoEntrada, caminhoSaida);
        if (problema != null) {
            System.out.println(Console.semAcentos(problema));
            return;
        }

        try {
            String fonte = LeitorArquivoFonte.ler(caminhoEntrada);
            TabelaPalavrasReservadas tabela = new TabelaPalavrasReservadas();
            AnalisadorLexico lexer = new AnalisadorLexico(fonte, tabela);

            List<Token> tokens = new ArrayList<>();
            Token token;
            // o analisador devolve null quando o texto acaba
            while ((token = lexer.proximoToken()) != null) {
                tokens.add(token);
            }

            EscritorArquivoSaida.escrever(caminhoSaida, tokens);
            System.out.println(tokens.size() + " tokens reconhecidos. Saida gravada em " + caminhoSaida);
        } catch (NoSuchFileException e) {
            System.out.println("Arquivo nao encontrado: " + e.getFile());
        } catch (IOException e) {
            System.out.println("Erro ao processar o arquivo: " + e.getMessage());
        }
    }

    // "prog.txt" -> "prog_saida.txt", na mesma pasta da entrada.
    private static String gerarNomeSaida(String caminhoEntrada) {
        File entrada = new File(caminhoEntrada);
        return new File(entrada.getParentFile(), EscritorArquivoSaida.nomeDeSaidaPara(entrada.getName())).getPath();
    }
}
