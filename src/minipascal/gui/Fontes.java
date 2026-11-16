package minipascal.gui;

import javafx.scene.Node;
import javafx.scene.text.Font;

import java.util.Arrays;
import java.util.List;

/**
 * Escolhe a fonte de largura fixa (para o código, os tokens, a pilha e as
 * tabelas SLR) entre as que existem na máquina. O CSS do JavaFX não aceita uma
 * lista de alternativas como o CSS da web: se a primeira fonte não existir,
 * ele usa a fonte padrão (proporcional), e o código fica desalinhado.
 */
final class Fontes {

    private static final List<String> PREFERIDAS = Arrays.asList(
            "Consolas", "DejaVu Sans Mono", "Menlo", "Liberation Mono", "Lucida Console", "Courier New");

    private static String monoespacada;

    private Fontes() {
    }

    // A primeira das preferidas instalada; "Monospaced" (fonte lógica do Java) se nenhuma estiver.
    static synchronized String monoespacada() {
        if (monoespacada == null) {
            List<String> instaladas = Font.getFamilies();
            monoespacada = "Monospaced";
            for (String f : PREFERIDAS) {
                if (instaladas.contains(f)) {
                    monoespacada = f;
                    break;
                }
            }
        }
        return monoespacada;
    }

    // Aplica a fonte de largura fixa ao nó (e, por herança, ao que está dentro dele).
    static void usarMonoespacada(Node... nos) {
        for (Node n : nos) {
            n.setStyle("-fx-font-family: \"" + monoespacada() + "\";");
        }
    }
}
