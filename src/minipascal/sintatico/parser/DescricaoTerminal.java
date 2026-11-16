package minipascal.sintatico.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Textos usados nas mensagens de erro sintatico: como mostrar o token
 * encontrado e os terminais que eram esperados.
 *
 *   encontrado identificador 'b'; esperado '+', '-', '*' ou fim do arquivo
 */
public final class DescricaoTerminal {

    private DescricaoTerminal() {
    }

    // O que foi encontrado, com o lexema real: "identificador 'b'", "'then'", "fim do arquivo".
    public static String encontrado(Terminal t) {
        if (t.isFimDeArquivo()) {
            return "fim do arquivo";
        }
        String lexema = t.getLexema();
        switch (t.getNome()) {
            case Terminal.ID:
                return "identificador '" + lexema + "'";
            case Terminal.NUMERO:
                return "número '" + lexema + "'";
            case Terminal.TIPO:
                return "tipo '" + lexema + "'";
            case Terminal.CONSTANTE_STRING:
                return "constante string " + lexema;
            case Terminal.CONSTANTE_CHAR:
                return "constante char " + lexema;
            default:
                return "'" + lexema + "'";
        }
    }

    // Nome de um terminal esperado: "identificador", "'then'", "fim do arquivo".
    public static String esperado(String nomeTerminal) {
        switch (nomeTerminal) {
            case Terminal.ID:
                return "identificador";
            case Terminal.NUMERO:
                return "número";
            case Terminal.TIPO:
                return "tipo";
            case Terminal.CONSTANTE_STRING:
                return "constante string";
            case Terminal.CONSTANTE_CHAR:
                return "constante char";
            case Terminal.FIM_DE_ARQUIVO:
                return "fim do arquivo";
            default:
                return "'" + nomeTerminal + "'";
        }
    }

    // "'+', '-' ou fim do arquivo"
    public static String listaDeEsperados(List<String> nomesTerminais) {
        List<String> textos = new ArrayList<>();
        for (String nome : nomesTerminais) {
            textos.add(esperado(nome));
        }
        // um so: sem "ou"
        if (textos.size() == 1) {
            return textos.get(0);
        }
        return String.join(", ", textos.subList(0, textos.size() - 1)) + " ou " + textos.get(textos.size() - 1);
    }
}
