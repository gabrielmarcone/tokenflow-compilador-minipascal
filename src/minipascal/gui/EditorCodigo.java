package minipascal.gui;

import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * O editor do código-fonte: a área de texto com uma coluna de números de linha
 * ao lado, que marca com "●" as linhas que têm erro, e a posição do cursor
 * ("Linha 12, coluna 5"). Como as mensagens de erro citam a linha, os números
 * evitam que o usuário tenha de contar linhas.
 *
 * A coluna de números é uma segunda área de texto, não editável, com a mesma
 * fonte, que rola junto com o código.
 */
public final class EditorCodigo extends BorderPane {

    private final TextArea area = new TextArea();
    private final TextArea numeros = new TextArea();
    private final Label posicao = new Label("Linha 1, coluna 1");
    private Set<Integer> linhasComErro = Collections.emptySet();
    private int linhasMostradas = -1;
    private boolean sincronizando = false;

    public EditorCodigo() {
        area.setId("areaCodigoFonte");
        area.getStyleClass().add("codigo-fonte");
        area.setWrapText(false);
        area.setPromptText("Digite ou cole aqui um programa Mini_Pascal, ou use Abrir (Ctrl+O)...");

        numeros.setId("numerosDeLinha");
        numeros.getStyleClass().add("numeros-linha");
        numeros.setEditable(false);
        numeros.setFocusTraversable(false);
        numeros.setWrapText(false);
        numeros.setMouseTransparent(true);

        Fontes.usarMonoespacada(area, numeros);
        HBox.setHgrow(area, Priority.ALWAYS);
        HBox editor = new HBox(numeros, area);
        editor.getStyleClass().add("editor");
        setCenter(editor);

        posicao.setId("labelPosicaoCursor");
        posicao.getStyleClass().add("posicao-cursor");
        setBottom(posicao);

        area.textProperty().addListener((obs, antigo, novo) -> atualizarNumeros(false));
        area.caretPositionProperty().addListener((obs, antigo, novo) -> atualizarPosicao());
        // Os números rolam junto com o código (nos dois sentidos, sem laço).
        area.scrollTopProperty().addListener((obs, antigo, novo) -> {
            if (!sincronizando) {
                sincronizando = true;
                numeros.setScrollTop(novo.doubleValue());
                sincronizando = false;
            }
        });
        atualizarNumeros(true);
    }

    public TextArea getArea() {
        return area;
    }

    public StringProperty textProperty() {
        return area.textProperty();
    }

    public String getText() {
        return area.getText();
    }

    public void setText(String texto) {
        area.setText(texto);
        area.positionCaret(0);
        area.setScrollTop(0);
    }

    // Marca com "●" as linhas com erro (vazio para tirar as marcas).
    public void marcarLinhasComErro(Set<Integer> linhas) {
        linhasComErro = new HashSet<>(linhas);
        atualizarNumeros(true);
    }

    /**
     * Seleciona a linha inteira e rola até ela, deixando-a no terço de cima da
     * área visível. Linha fora do texto é ignorada.
     */
    public void selecionarLinha(int linha) {
        String texto = area.getText();
        if (texto == null || linha < 1) {
            return;
        }
        int inicio = 0;
        for (int l = 1; l < linha; l++) {
            int quebra = texto.indexOf('\n', inicio);
            if (quebra < 0) {
                return;
            }
            inicio = quebra + 1;
        }
        int fim = texto.indexOf('\n', inicio);
        if (fim < 0) {
            fim = texto.length();
        }
        if (fim > inicio && texto.charAt(fim - 1) == '\r') {
            fim--;
        }
        area.selectRange(fim, inicio);
        double alturaLinha = alturaDaLinha();
        double visivel = area.getHeight() > 0 ? area.getHeight() : 400;
        area.setScrollTop(Math.max(0, (linha - 1) * alturaLinha - visivel / 3));
    }

    // Linha do cursor (1, 2, 3...).
    public int linhaDoCursor() {
        String texto = area.getText();
        int linha = 1;
        for (int i = 0; i < area.getCaretPosition() && i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') {
                linha++;
            }
        }
        return linha;
    }

    private void atualizarPosicao() {
        String texto = area.getText();
        int cursor = Math.min(area.getCaretPosition(), texto.length());
        int linha = 1;
        int inicioDaLinha = 0;
        for (int i = 0; i < cursor; i++) {
            if (texto.charAt(i) == '\n') {
                linha++;
                inicioDaLinha = i + 1;
            }
        }
        posicao.setText("Linha " + linha + ", coluna " + (cursor - inicioDaLinha + 1));
    }

    /*
     * Reescreve a coluna de números quando o número de linhas muda (ou quando
     * as marcas de erro mudam). Uma linha a mais no fim compensa a barra de
     * rolagem horizontal do código, para que o fim fique alinhado.
     */
    private void atualizarNumeros(boolean forcar) {
        String texto = area.getText();
        int linhas = 1;
        if (texto != null) {
            for (int i = 0; i < texto.length(); i++) {
                if (texto.charAt(i) == '\n') {
                    linhas++;
                }
            }
        }
        if (!forcar && linhas == linhasMostradas) {
            return;
        }
        linhasMostradas = linhas;
        int largura = Math.max(3, String.valueOf(linhas).length());
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= linhas; i++) {
            String n = String.valueOf(i);
            sb.append(linhasComErro.contains(i) ? "●" : " ");
            for (int k = n.length(); k < largura; k++) {
                sb.append(' ');
            }
            sb.append(n).append('\n');
        }
        sb.append(' ');
        double rolagem = area.getScrollTop();
        numeros.setText(sb.toString());
        numeros.setPrefColumnCount(largura + 1);
        numeros.setMinWidth(USE_PREF_SIZE);
        numeros.setMaxWidth(USE_PREF_SIZE);
        numeros.setScrollTop(rolagem);
        atualizarPosicao();
    }

    private double alturaDaLinha() {
        Text medida = new Text("Mg");
        medida.setFont(area.getFont());
        return medida.getLayoutBounds().getHeight();
    }
}
