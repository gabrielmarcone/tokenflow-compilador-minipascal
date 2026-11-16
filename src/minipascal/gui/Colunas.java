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
 * Larguras e células das colunas das tabelas da interface.
 *
 * O ajuste automático do JavaFX (CONSTRAINED_RESIZE_POLICY) reparte a
 * largura em proporção às larguras iniciais: colunas curtas como "Nº" ficam
 * largas demais e as de texto longo ficam cortadas. Aqui as colunas curtas
 * mantêm a sua largura e só as de texto ocupam o espaço que sobra.
 */
final class Colunas {

    // Espaço da barra de rolagem vertical (16) e uma folga, para não aparecer a horizontal.
    private static final double BARRA_DE_ROLAGEM = 17;

    private Colunas() {
    }

    /*
     * A coluna dada fica com a largura útil da tabela (sem as bordas e a barra
     * de rolagem vertical) menos a das outras colunas, mas nunca menos que o
     * mínimo.
     */
    static void ocuparEspacoQueSobra(TableView<?> tabela, TableColumn<?, ?> coluna, double minimo) {
        repartirEspacoQueSobra(tabela, minimo, coluna);
    }

    // Como ocuparEspacoQueSobra, mas o espaço é dividido igualmente entre as colunas dadas.
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
     * Célula que quebra o texto em várias linhas quando ele não cabe na
     * largura da coluna (a linha da tabela fica mais alta), em vez de cortá-lo.
     */
    static <T> TableCell<T, String> celulaQueQuebra() {
        return new TableCell<T, String>() {
            private final Text texto = new Text();

            {
                texto.getStyleClass().add("texto-quebrado");
                // A largura da coluna já é conhecida antes do desenho da célula
                // (a da célula, não), e a altura da linha depende dela.
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
