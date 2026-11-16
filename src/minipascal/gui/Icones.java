package minipascal.gui;

import javafx.scene.Node;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 * Icones desenhados em vetor (SVG), para os botoes e a faixa de resultado.
 * Emojis e simbolos de fonte dependem das fontes instaladas e podem aparecer
 * como quadrados; um SVGPath aparece igual em qualquer maquina.
 */
final class Icones {

    // Caminhos SVG numa grade de 20 x 20 (M = move, h/v/l = linhas, a = arco, z = fecha).
    private static final String PASTA = "M2 4h6l2 2h8v11H2z";
    private static final String DISCO = "M3 3h11l3 3v11H3z M6 3v5h7V3 M6 12h8v5H6z";
    private static final String PLAY = "M5 3l11 7-11 7z";
    private static final String CERTO = "M3 10l5 5 9-10-2-2-7 8-3-3z";
    private static final String ERRADO = "M5 3l5 5 5-5 2 2-5 5 5 5-2 2-5-5-5 5-2-2 5-5-5-5z";
    private static final String ATENCAO = "M10 2l9 16H1z M9 7h2v6H9z M9 14h2v2H9z";
    private static final String INFO = "M10 1a9 9 0 1 0 0.01 0z M9 8h2v7H9z M9 4h2v2H9z";
    private static final String ANTERIOR = "M13 3L6 10l7 7z";
    private static final String PROXIMO = "M7 3l7 7-7 7z";
    private static final String PRIMEIRO = "M4 3h2v14H4z M15 3L8 10l7 7z";
    private static final String ULTIMO = "M14 3h2v14h-2z M5 3l7 7-7 7z";

    private Icones() {
    }

    static Node pasta() {
        return icone(PASTA, "icone");
    }

    static Node disco() {
        return icone(DISCO, "icone");
    }

    static Node executar() {
        return icone(PLAY, "icone");
    }

    static Node certo() {
        return icone(CERTO, "icone-resultado");
    }

    static Node errado() {
        return icone(ERRADO, "icone-resultado");
    }

    static Node atencao() {
        return icone(ATENCAO, "icone-resultado");
    }

    static Node info() {
        return icone(INFO, "icone-resultado");
    }

    static Node anterior() {
        return icone(ANTERIOR, "icone-navegacao");
    }

    static Node proximo() {
        return icone(PROXIMO, "icone-navegacao");
    }

    static Node primeiro() {
        return icone(PRIMEIRO, "icone-navegacao");
    }

    static Node ultimo() {
        return icone(ULTIMO, "icone-navegacao");
    }

    // Cria o icone; a cor e o tamanho vem do CSS, pela classe.
    private static Node icone(String caminho, String classe) {
        SVGPath svg = new SVGPath();
        svg.setContent(caminho);
        // Par-impar: as partes de dentro (o "!" do aviso, o "i") ficam vazadas.
        svg.setFillRule(FillRule.EVEN_ODD);
        svg.getStyleClass().add(classe);
        return svg;
    }
}
