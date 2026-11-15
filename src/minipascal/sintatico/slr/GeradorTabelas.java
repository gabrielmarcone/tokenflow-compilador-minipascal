package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.GramaticaInvalidaException;
import minipascal.sintatico.gramatica.LeitorGramatica;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;

/**
 * Linha de comando do gerador: lê uma gramática, constrói a tabela SLR(1) e
 * grava o relatório (.txt) e a tabela (.csv).
 *
 *   java -cp TokenFlow.jar minipascal.sintatico.slr.GeradorTabelas <gramatica.txt> [pasta-saida]
 *
 * Se o argumento for só um nome (sem pasta) e não existir no disco, procura
 * uma gramática com esse nome entre as que acompanham o programa (ex.:
 * expressoes.txt). Sem pasta de saída, os
 * arquivos são gravados na pasta atual. Sai com código 1 se a
 * gramática for inválida ou tiver conflitos (o relatório é gravado mesmo
 * assim, mostrando onde estão os conflitos).
 *
 * As mensagens do console não têm acentos, como no Main da Unidade I: o
 * console do Windows com Java 8 não mostra acentos corretamente.
 */
public final class GeradorTabelas {

    private GeradorTabelas() {
    }

    public static void main(String[] args) {
        System.exit(executar(args));
    }

    // Devolve o código de saída: 0 = tabela gerada sem conflitos; 1 = qualquer problema.
    public static int executar(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Uso: java -cp TokenFlow.jar minipascal.sintatico.slr.GeradorTabelas"
                    + " <gramatica.txt> [pasta-saida]");
            return 1;
        }
        try {
            Path arquivo = Paths.get(args[0]);
            Path nome = arquivo.getFileName();
            if (nome == null) {
                System.out.println("Arquivo nao encontrado: " + args[0]);
                return 1;
            }
            String nomeArquivo = nome.toString();
            String nomeBase = nomeArquivo.endsWith(".txt")
                    ? nomeArquivo.substring(0, nomeArquivo.length() - 4) : nomeArquivo;
            Gramatica gramatica;
            if (Files.isRegularFile(arquivo)) {
                gramatica = LeitorGramatica.lerArquivo(arquivo.toString());
            } else if (arquivo.getParent() == null && LeitorGramatica.existeRecurso(nomeArquivo)) {
                // só um nome, sem pasta: pode ser uma das gramáticas embutidas
                gramatica = LeitorGramatica.lerRecurso(nomeArquivo);
                System.out.println("Usando a gramatica embutida no programa: " + nomeArquivo);
            } else {
                System.out.println("Arquivo nao encontrado: " + args[0]);
                return 1;
            }
            Path pasta = args.length == 2 ? Paths.get(args[1]) : Paths.get(".");
            Files.createDirectories(pasta);

            AnaliseSLR analise = new AnaliseSLR(gramatica);
            System.out.println("Gramatica: " + (gramatica.getProducoes().size() - 1) + " producoes, "
                    + (gramatica.getTerminais().size() - 1) + " terminais (mais $), "
                    + gramatica.getNaoTerminais().size() + " nao terminais.");
            System.out.println("Estados LR(0): " + analise.getColecao().tamanho() + ".");

            List<Path> gravados = ExportadorTabela.gravar(analise, nomeArquivo, pasta, nomeBase);
            if (analise.temConflitos()) {
                System.out.println("A gramatica NAO e SLR(1): " + analise.getConflitos().size() + " conflito(s).");
                for (ConflitoSLR c : analise.getConflitos()) {
                    System.out.println("  estado " + c.getEstado() + ", terminal '" + c.getTerminal()
                            + "': " + c.getTipo() + " entre " + c.getAcoes());
                }
            } else {
                System.out.println("Nenhum conflito: a gramatica e SLR(1).");
            }
            for (Path p : gravados) {
                System.out.println("Gravado: " + p);
            }
            return analise.temConflitos() ? 1 : 0;
        } catch (GramaticaInvalidaException e) {
            System.out.println("Gramatica invalida. " + semAcentos(e.getMessage()));
            return 1;
        } catch (IOException e) {
            System.out.println("Erro de leitura ou gravacao: " + semAcentos(e.getMessage()));
            return 1;
        } catch (InvalidPathException e) {
            System.out.println("Caminho invalido: " + semAcentos(e.getMessage()));
            return 1;
        }
    }

    // "Gramática, linha 3: não terminal" -> "Gramatica, linha 3: nao terminal"
    static String semAcentos(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
