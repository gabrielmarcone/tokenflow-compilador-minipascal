package minipascal.io;

import minipascal.lexico.model.Token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Grava a lista de tokens no arquivo de saida, um par <lexema, token> por
 * linha (usa o Token.toString(), ja no formato exigido), sempre em UTF-8.
 */
public class EscritorArquivoSaida {

    /** "prog.txt" -> "prog_saida.txt"; sem extensao, so acrescenta o sufixo. */
    public static String nomeDeSaidaPara(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        String base = ponto > 0 ? nomeArquivo.substring(0, ponto) : nomeArquivo;
        return base + "_saida.txt";
    }

    // Um token por linha, "lexema<TAB>classe", em UTF-8.
    public static void escrever(String caminho, List<Token> tokens) throws IOException {
        StringBuilder conteudo = new StringBuilder();
        for (Token token : tokens) {
            conteudo.append(token.toString()).append('\n');
        }
        Files.write(Paths.get(caminho), conteudo.toString().getBytes(StandardCharsets.UTF_8));
    }
}