package minipascal.gui;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Larguras e celulas das colunas das tabelas da interface.
 *
 * O ajuste automatico do JavaFX (CONSTRAINED_RESIZE_POLICY) reparte a
 * largura em proporcao as larguras iniciais: colunas curtas como "No" ficam
 * largas demais e as de texto longo ficam cortadas. Aqui as colunas curtas
 * mantem a sua largura e so as de texto ocupam o espaco que sobra.
 */
final class Colunas {

    // Espaco da barra de rolagem vertical (16) e uma folga, para nao aparecer a horizontal.
    private static final double BARRA_DE_ROLAGEM = 17;

    private Colunas() {
    }

    /*
     * A coluna dada fica com a largura util da tabela (sem as bordas e a barra
     * de rolagem vertical) menos a das outras colunas, mas nunca menos que o
     * minimo.
     */
    static void ocuparEspacoQueSobra(TableView<?> tabela, TableColumn<?, ?> coluna, double minimo) {
        repartirEspacoQueSobra(tabela, minimo, coluna);
    }

    // Como ocuparEspacoQueSobra, mas o espaco e dividido igualmente entre as colunas dadas.
    static void repartirEspacoQueSobra(TableView<?> tabela, double minimo, TableColumn<?, ?>... colunas) {
        List<TableColumn<?, ?>> elas = Arrays.asList(colunas);
        List<Observable> dependencias = new ArrayList<>();
        dependencias.add(tabela.widthProperty());
        for (TableColumn<?, ?> c : tabela.getColumns()) {
            if (!elas.contains(c)) {
                dependencias.add(c.widthProperty());
            }
        }
        for (TableColumn<?, ?> coluna : colunas) {
            coluna.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> {
                double outras = 0;
                for (TableColumn<?, ?> c : tabela.getColumns()) {
                    if (!elas.contains(c)) {
                        outras += c.getWidth();
                    }
                }
                double util = tabela.getWidth() - tabela.getInsets().getLeft() - tabela.getInsets().getRight()
                        - BARRA_DE_ROLAGEM;
                return Math.max(minimo, Math.floor((util - outras) / colunas.length));
            }, dependencias.toArray(new Observable[0])));
        }
    }

    /*
     * Celula que quebra o texto em varias linhas quando ele nao cabe na
     * largura da coluna (a linha da tabela fica mais alta), em vez de corta-lo.
     */
    static <T> TableCell<T, String> celulaQueQuebra() {
        return new TableCell<T, String>() {
            private final Text texto = new Text();

            {
                texto.getStyleClass().add("texto-quebrado");
                // A largura da coluna ja e conhecida antes do desenho da celula
                // (a da celula, nao), e a altura da linha depende dela.
                tableColumnProperty().addListener((obs, antiga, nova) -> {
                    if (nova != null) {
                        texto.wrappingWidthProperty().bind(nova.widthProperty().subtract(12));
                    }
                });
                setMinHeight(USE_PREF_SIZE);
            }

            @Override
            protected void updateItem(String item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(null);
                if (vazio || item == null) {
                    setGraphic(null);
                } else {
                    texto.setText(item);
                    setGraphic(texto);
                }
            }
        };
    }
}
