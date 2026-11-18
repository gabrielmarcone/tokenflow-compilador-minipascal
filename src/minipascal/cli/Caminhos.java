package minipascal.cli;

import java.io.File;

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
        if (arquivoSaida.equals(arquivoEntrada)) {
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
}
