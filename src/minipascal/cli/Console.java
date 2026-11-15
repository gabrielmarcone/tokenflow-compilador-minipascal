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
}
