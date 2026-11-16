package minipascal.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Ponto de entrada da interface JavaFX. Responsabilidade unica: carregar o
 * FXML, montar a Scene, aplicar o CSS e abrir a janela. Nenhuma logica de
 * analise entra aqui: isso e papel do AnalisadorController.
 *
 * A janela abre com 1280 x 800 quando cabe; em telas menores, ocupa a area
 * util da tela (sem passar por baixo da barra de tarefas).
 */
public class MainApp extends Application {

    private static final double LARGURA = 1280;
    private static final double ALTURA = 800;
    private static final double LARGURA_MINIMA = 1000;
    private static final double ALTURA_MINIMA = 640;

    @Override
    public void start(Stage stage) throws Exception {
        // O FXML descreve a janela e cria o AnalisadorController.
        Parent root = FXMLLoader.load(getClass().getResource("Main.fxml"));
        // Area util da tela (sem a barra de tarefas): a janela nunca passa dela.
        Rectangle2D tela = Screen.getPrimary().getVisualBounds();
        double largura = Math.min(LARGURA, tela.getWidth());
        double altura = Math.min(ALTURA, tela.getHeight());
        Scene scene = new Scene(root, largura, altura);
        // cores, fontes e espacamentos (application.css)
        scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

        stage.setTitle("TokenFlow - Compilador Mini_Pascal");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("icon.png")));
        stage.setScene(scene);
        stage.setMinWidth(Math.min(LARGURA_MINIMA, tela.getWidth()));
        stage.setMinHeight(Math.min(ALTURA_MINIMA, tela.getHeight()));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
