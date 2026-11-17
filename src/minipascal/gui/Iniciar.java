package minipascal.gui;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.text.Normalizer;

/**
 * Ponto de entrada do TokenFlow.jar (Main-Class do manifesto).
 *
 * Esta classe nao usa nada do JavaFX, de proposito: ela so confere se o Java
 * em uso tem o JavaFX e, se tiver, abre a interface (MainApp). Se nao tiver
 * (Java 11 ou mais novo sem JavaFX, ou um OpenJDK 8 sem ele), mostra uma
 * janela simples do Swing, que existe em todo Java, dizendo como abrir o
 * programa, em vez de falhar em silencio no duplo clique.
 *
 *   java -jar TokenFlow.jar               abre a interface
 *   java -jar TokenFlow.jar --verificar   so confere: codigo de saida 0 se
 *                                         este Java tem JavaFX, 2 se nao tem
 *                                         (usado pelo TokenFlow.bat)
 */
public final class Iniciar {

    // Codigo de saida quando este Java nao tem JavaFX.
    static final int SEM_JAVAFX = 2;

    private static final String PAGINA_DE_VERSOES =
            "https://github.com/gabrielmarcone/tokenflow-compilador-minipascal/releases";

    private Iniciar() {
    }

    public static void main(String[] args) {
        boolean temJavaFx = temJavaFx();
        if (args.length == 1 && args[0].equals("--verificar")) {
            System.exit(temJavaFx ? 0 : SEM_JAVAFX);
        }
        if (!temJavaFx) {
            avisar("Este Java não tem o JavaFX, que a interface do TokenFlow usa.\n"
                    + "Java em uso: " + System.getProperty("java.version")
                    + " (" + System.getProperty("java.home") + ")\n\n"
                    + COMO_ABRIR);
            System.exit(SEM_JAVAFX);
        }
        try {
            MainApp.main(args);
        } catch (Throwable erro) {
            // Pelo duplo clique nao ha console: sem esta janela, um erro na
            // abertura passaria despercebido.
            erro.printStackTrace();
            avisar("Não foi possível abrir a interface:\n" + resumo(erro) + "\n\n" + COMO_ABRIR);
            System.exit(1);
        }
    }

    private static final String COMO_ABRIR =
            "Para abrir a interface:\n"
            + "  - no Windows, use o TokenFlow.bat: ele procura um Java com JavaFX e, se não\n"
            + "    achar, oferece baixar um Java portátil para a pasta do projeto;\n"
            + "  - ou baixe o pacote com o Java incluído (TokenFlow-windows.zip) em\n"
            + "    " + PAGINA_DE_VERSOES + ";\n"
            + "  - ou instale um Java com JavaFX (por exemplo, o Liberica JDK \"Full\").\n\n"
            + "A linha de comando funciona com qualquer Java 8 ou mais novo, por exemplo:\n"
            + "  java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica programa.txt saida.txt";

    /*
     * Confere se o JavaFX esta disponivel sem carregar a classe de fato
     * (initialize = false): so procura.
     */
    static boolean temJavaFx() {
        try {
            Class.forName("javafx.application.Application", false, Iniciar.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /*
     * Mostra a mensagem no console (sem acentos, como o resto da linha de
     * comando) e, quando ha tela, numa janela do Swing.
     */
    private static void avisar(String mensagem) {
        System.out.println(semAcentos(mensagem));
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // fica a aparencia padrao do Swing
        }
        try {
            JOptionPane.showMessageDialog(null, mensagem, "TokenFlow", JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException e) {
            // sem janela, fica so o texto no console
        }
    }

    // Primeira linha do erro e a da causa, se houver (o resto vai para o console).
    private static String resumo(Throwable erro) {
        Throwable raiz = erro;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String texto = erro.toString();
        if (raiz != erro) {
            texto += "\nCausa: " + raiz;
        }
        return texto;
    }

    private static String semAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
