package minipascal.cli;

import java.text.Normalizer;

/**
 * Texto para o console. No Windows, o Java 8 não mostra acentos corretamente
 * no console (a linha de comando da Unidade I já evitava acentos por isso); as mensagens
 * impressas passam por aqui. Arquivos de saída continuam em UTF-8, com acentos.
 */
public final class Console {

    private Console() {
    }

    // "Erro sintático na linha 3" -> "Erro sintatico na linha 3"
    public static String semAcentos(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /*
     * So ASCII, para as mensagens das suites de teste: tira os acentos, troca
     * alguns simbolos por equivalentes ("->", "eps", "...") e escreve os outros
     * caracteres como codigo Unicode ("\\u00a7"), que qualquer console mostra.
     */
    public static String ascii(String texto) {
        String sem = semAcentos(texto).replace("\u2014", "-").replace("\u2192", "->").replace("\u2026", "...")
                .replace("\u03b5", "eps").replace("\u00ab", "<<").replace("\u00bb", ">>").replace("\u25cf", "*");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sem.length(); i++) {
            char c = sem.charAt(i);
            if (c < 128) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04x", (int) c));
            }
        }
        return sb.toString();
    }
}
