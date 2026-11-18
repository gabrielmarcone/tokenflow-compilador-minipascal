package minipascal.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Confere os caminhos de entrada e de saida da linha de comando antes da
 * analise, para que um problema neles apareca com uma mensagem clara (e nada
 * seja impresso antes dela). Sem isso, uma pasta de saida que nao existe so
 * dava erro na hora de gravar, depois do resultado, e com a mensagem
 * "Arquivo nao encontrado" apontando para a entrada.
 */
final class Caminhos {

    private Caminhos() {
    }

    /*
     * Devolve a mensagem do primeiro problema encontrado, ou null se os
     * caminhos servem. saida pode ser null (sem arquivo de saida). Um arquivo
     * de entrada que nao existe fica para a leitura ("Arquivo nao encontrado").
     */
    static String problema(String entrada, String saida) {
        File arquivoEntrada = new File(entrada).getAbsoluteFile();
        if (arquivoEntrada.isDirectory()) {
            return "A entrada e uma pasta, nao um arquivo: " + entrada;
        }
        if (saida == null) {
            return null;
        }
        File arquivoSaida = new File(saida).getAbsoluteFile();
        // Gravar a saida por cima da entrada apagaria o programa.
        if (mesmoArquivo(arquivoEntrada, arquivoSaida)) {
            return "O arquivo de saida nao pode ser o mesmo da entrada.";
        }
        if (arquivoSaida.isDirectory()) {
            return "A saida e uma pasta, nao um arquivo: " + saida;
        }
        File pasta = arquivoSaida.getParentFile();
        if (pasta != null && !pasta.isDirectory()) {
            return "A pasta do arquivo de saida nao existe: " + pasta.getPath();
        }
        return null;
    }

    /*
     * Os dois caminhos levam ao mesmo arquivo? Nao basta comparar o texto:
     * "prog.txt", "./prog.txt", "pasta/../prog.txt" (e, no Windows, "PROG.TXT")
     * sao o mesmo arquivo. Primeiro compara os caminhos simplificados; se o
     * arquivo de saida ja existe, pergunta ao sistema se e o mesmo da entrada.
     */
    static boolean mesmoArquivo(File a, File b) {
        try {
            Path pa = a.toPath().toAbsolutePath().normalize();
            Path pb = b.toPath().toAbsolutePath().normalize();
            if (pa.equals(pb)) {
                return true;
            }
            return Files.exists(pa) && Files.exists(pb) && Files.isSameFile(pa, pb);
        } catch (InvalidPathException | IOException e) {
            // caminho que o sistema nao aceita: fica a comparacao simples
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
