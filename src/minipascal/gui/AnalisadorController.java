package minipascal.gui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.io.EscritorArquivoSaida;
import minipascal.io.LeitorArquivoFonte;
import minipascal.lexico.model.TipoToken;
import minipascal.lexico.model.Token;
import minipascal.semantico.CategoriaSimbolo;
import minipascal.semantico.Compilador;
import minipascal.semantico.ResultadoCompilacao;
import minipascal.semantico.Simbolo;
import minipascal.sintatico.parser.PassoAnalise;
import minipascal.sintatico.parser.RastroAnalise;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Controlador da janela (Main.fxml). Liga o editor, a análise completa
 * (Compilador: léxica, sintática e semântica, com o rastro da pilha) e as abas.
 *
 * Decisões de interface:
 *   - o código fica sempre visível à esquerda; o resultado, à direita;
 *   - a faixa de resultado responde à pergunta principal (o programa está
 *     certo?) e avisa quando o código mudou depois da última análise;
 *   - as abas seguem as fases do compilador: Erros, Tokens (léxica), Pilha
 *     (sintática), Símbolos (semântica) e a descrição da linguagem (Gramática
 *     e SLR(1)); o título de cada aba mostra quantos itens ela tem;
 *   - selecionar um erro, um token, um passo ou um símbolo mostra a linha no
 *     código; selecionar um passo também destaca a célula da tabela SLR(1)
 *     que o analisador usou;
 *   - atalhos: Ctrl+O abre, Ctrl+S salva o resultado, F5 ou Ctrl+Enter analisa.
 */
public class AnalisadorController implements Initializable {

    @FXML private BorderPane raiz;
    @FXML private Button btnAbrir;
    @FXML private Button btnSalvar;
    @FXML private Button btnAjuda;
    @FXML private Button btnAnalisar;
    @FXML private Label labelArquivoAtual;
    @FXML private SplitPane splitPrincipal;

    @FXML private EditorCodigo editor;

    @FXML private HBox faixaResultado;
    @FXML private StackPane iconeResultado;
    @FXML private Label labelResultado;
    @FXML private Label labelDetalheResultado;

    @FXML private TabPane abasResultado;
    @FXML private Tab abaErros;
    @FXML private Tab abaTokens;
    @FXML private Tab abaPilha;
    @FXML private Tab abaSimbolos;
    @FXML private Tab abaSLR;

    @FXML private TableView<ErroCompilacao> tabelaErros;
    @FXML private TableColumn<ErroCompilacao, Number> colErroLinha;
    @FXML private TableColumn<ErroCompilacao, String> colErroFase;
    @FXML private TableColumn<ErroCompilacao, String> colErroMensagem;

    @FXML private TableView<Token> tabelaTokens;
    @FXML private TableColumn<Token, Number> colNumero;
    @FXML private TableColumn<Token, String> colLexema;
    @FXML private TableColumn<Token, String> colTipo;
    @FXML private TableColumn<Token, Number> colLinha;
    @FXML private Button btnSalvarTokens;

    @FXML private Button btnPrimeiroPasso;
    @FXML private Button btnPassoAnterior;
    @FXML private Button btnProximoPasso;
    @FXML private Button btnUltimoPasso;
    @FXML private Button btnProximoErro;
    @FXML private Button btnVerNaTabela;
    @FXML private Label labelPasso;
    @FXML private TableView<PassoAnalise> tabelaPassos;
    @FXML private TableColumn<PassoAnalise, Number> colPassoNumero;
    @FXML private TableColumn<PassoAnalise, String> colPassoPilha;
    @FXML private TableColumn<PassoAnalise, String> colPassoSimbolos;
    @FXML private TableColumn<PassoAnalise, String> colPassoEntrada;
    @FXML private TableColumn<PassoAnalise, String> colPassoAcao;
    @FXML private Label labelDetalhePasso;

    @FXML private Label labelAvisoSimbolos;
    @FXML private TableView<Simbolo> tabelaSimbolos;
    @FXML private TableColumn<Simbolo, String> colSimboloEscopo;
    @FXML private TableColumn<Simbolo, Number> colSimboloNivel;
    @FXML private TableColumn<Simbolo, String> colSimboloNome;
    @FXML private TableColumn<Simbolo, String> colSimboloCategoria;
    @FXML private TableColumn<Simbolo, String> colSimboloTipo;
    @FXML private TableColumn<Simbolo, String> colSimboloLinha;
    @FXML private TableColumn<Simbolo, String> colSimboloParametros;

    @FXML private Label labelContagemTokens;
    @FXML private Label labelContagemErros;
    @FXML private Label labelContagemPassos;
    @FXML private Label labelStatus;

    // Quantos lexemas a coluna Entrada mostra (o resto vira "…"; o detalhe do passo mostra mais).
    private static final int LEXEMAS_NA_ENTRADA = 8;
    private static final int LEXEMAS_NO_DETALHE = 40;

    private final Compilador compilador = new Compilador();
    private PainelSLR painelSLR;

    private ResultadoCompilacao resultado;
    private RastroAnalise rastro;
    private String textoAnalisado;
    private String nomeArquivo;
    private File pastaAtual;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        painelSLR = new PainelSLR(compilador.getTabelaSLR());
        abaSLR.setContent(painelSLR.getConteudo());

        Fontes.usarMonoespacada(tabelaTokens, tabelaPassos, labelDetalhePasso);
        // A última coluna de cada tabela ocupa o espaço que sobra (sem barra de
        // rolagem horizontal enquanto couber); o texto que não cabe termina em
        // "…" e aparece inteiro na dica.
        Colunas.ocuparEspacoQueSobra(tabelaErros, colErroMensagem, 220);
        Colunas.ocuparEspacoQueSobra(tabelaTokens, colTipo, 150);
        Colunas.ocuparEspacoQueSobra(tabelaPassos, colPassoAcao, 200);
        Colunas.ocuparEspacoQueSobra(tabelaSimbolos, colSimboloParametros, 160);
        labelResultado.setMinHeight(Region.USE_PREF_SIZE);
        labelDetalheResultado.setMinHeight(Region.USE_PREF_SIZE);
        configurarBotoes();
        configurarTabelaErros();
        configurarTabelaTokens();
        configurarTabelaPassos();
        configurarTabelaSimbolos();
        configurarAbas();

        editor.textProperty().addListener((obs, antigo, novo) -> textoMudou(novo));
        raiz.sceneProperty().addListener((obs, antiga, nova) -> {
            if (nova != null) {
                configurarAtalhos(nova);
            }
        });
        limparResultado();
        mostrarFaixaInicial();
        // O cursor começa no editor: dá para digitar o programa logo ao abrir.
        javafx.application.Platform.runLater(() -> editor.getArea().requestFocus());
    }

    // ------------------------------------------------------------ montagem

    private void configurarBotoes() {
        btnAbrir.setGraphic(Icones.pasta());
        btnSalvar.setGraphic(Icones.disco());
        btnAnalisar.setGraphic(Icones.executar());
        btnPrimeiroPasso.setGraphic(Icones.primeiro());
        btnPassoAnterior.setGraphic(Icones.anterior());
        btnProximoPasso.setGraphic(Icones.proximo());
        btnUltimoPasso.setGraphic(Icones.ultimo());
        btnAbrir.setTooltip(new Tooltip("Abrir um programa (.txt)  —  Ctrl+O"));
        btnSalvar.setTooltip(new Tooltip("Salvar o resultado da análise (OK ou os erros) num .txt  —  Ctrl+S"));
        btnAnalisar.setTooltip(new Tooltip("Análise léxica, sintática e semântica do código  —  F5 ou Ctrl+Enter"));
        btnAjuda.setTooltip(new Tooltip("Como usar o TokenFlow"));
        btnPrimeiroPasso.setTooltip(new Tooltip("Primeiro passo"));
        btnPassoAnterior.setTooltip(new Tooltip("Passo anterior"));
        btnProximoPasso.setTooltip(new Tooltip("Próximo passo"));
        btnUltimoPasso.setTooltip(new Tooltip("Último passo"));
        btnProximoErro.setTooltip(new Tooltip("Vai para o próximo erro sintático e a recuperação"));
        btnVerNaTabela.setTooltip(new Tooltip("Mostra na tabela ACTION / GOTO a célula usada neste passo"));
        btnSalvarTokens.setTooltip(new Tooltip("Salva a lista lexema / classe no formato da Unidade I"));
    }

    private void configurarAtalhos(Scene cena) {
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::abrirArquivo);
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!btnSalvar.isDisabled()) {
                salvarResultado();
            }
        });
        Runnable analisarSePuder = () -> {
            if (!btnAnalisar.isDisabled()) {
                analisar();
            }
        };
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), analisarSePuder);
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), analisarSePuder);
    }

    private void configurarAbas() {
        abaErros.setTooltip(new Tooltip("Erros léxicos, sintáticos e semânticos, com a linha"));
        abaTokens.setTooltip(new Tooltip("Análise léxica: os tokens do programa"));
        abaPilha.setTooltip(new Tooltip("Análise sintática SLR(1): os movimentos da pilha, passo a passo"));
        abaSimbolos.setTooltip(new Tooltip("Análise semântica: a tabela de símbolos"));
        abaSLR.setTooltip(new Tooltip("A gramática e as tabelas SLR(1) geradas a partir dela"));
    }

    private void configurarTabelaErros() {
        colErroLinha.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getLinha()));
        colErroFase.setCellValueFactory(d -> new SimpleStringProperty(fase(d.getValue().getCategoria())));
        colErroMensagem.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getMensagem()));
        colErroMensagem.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        tabelaErros.setRowFactory(t -> linhaComClasse(e -> "erro-" + e.getCategoria().name().toLowerCase()));
        tabelaErros.getSelectionModel().selectedItemProperty().addListener((obs, antigo, erro) -> {
            if (erro != null) {
                editor.selecionarLinha(erro.getLinha());
            }
        });
    }

    private void configurarTabelaTokens() {
        colNumero.setCellValueFactory(d -> new SimpleIntegerProperty(tabelaTokens.getItems().indexOf(d.getValue()) + 1));
        colLexema.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLexema()));
        colLexema.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        colTipo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTipo().getDescricao()));
        colLinha.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getLinha()));
        tabelaTokens.setRowFactory(t -> linhaComClasse(
                token -> token.getTipo() == TipoToken.ERRO_LEXICO ? "erro-lexico" : null));
        tabelaTokens.getSelectionModel().selectedItemProperty().addListener((obs, antigo, token) -> {
            if (token != null) {
                editor.selecionarLinha(token.getLinha());
            }
        });
    }

    private void configurarTabelaPassos() {
        colPassoNumero.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getNumero()));
        colPassoPilha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().pilhaComoTexto()));
        colPassoSimbolos.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().simbolosComoTexto()));
        colPassoEntrada.setCellValueFactory(d -> new SimpleStringProperty(
                RastroAnalise.entradaRestante(d.getValue(), resultado.getTokens(), LEXEMAS_NA_ENTRADA)));
        colPassoAcao.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescricao()));
        // Pilha e símbolos crescem para a direita: quando não cabem, o que se
        // corta é a base (à esquerda), e o topo continua visível.
        colPassoPilha.setCellFactory(c -> celulaComDica(OverrunStyle.LEADING_ELLIPSIS));
        colPassoSimbolos.setCellFactory(c -> celulaComDica(OverrunStyle.LEADING_ELLIPSIS));
        colPassoEntrada.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        colPassoAcao.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        tabelaPassos.setRowFactory(t -> linhaComClasse(p -> "passo-" + p.getTipo().name().toLowerCase()));
        tabelaPassos.getSelectionModel().selectedItemProperty().addListener((obs, antigo, passo) -> mostrarPasso(passo));
    }

    private void configurarTabelaSimbolos() {
        colSimboloEscopo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEscopo()));
        colSimboloNivel.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getNivel()));
        colSimboloNome.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNome()));
        colSimboloCategoria.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoria().getDescricao()));
        colSimboloTipo.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTipo() == null ? "-" : d.getValue().getTipo().getNome()));
        colSimboloLinha.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getLinha() == 0 ? "-" : String.valueOf(d.getValue().getLinha())));
        colSimboloParametros.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().descreverParametros()));
        colSimboloParametros.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        tabelaSimbolos.getSelectionModel().selectedItemProperty().addListener((obs, antigo, s) -> {
            if (s != null && s.getLinha() > 0) {
                editor.selecionarLinha(s.getLinha());
            }
        });
    }

    // --------------------------------------------------------------- ações

    @FXML
    private void abrirArquivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir programa Mini_Pascal");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Arquivos de texto (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));
        if (pastaAtual != null && pastaAtual.isDirectory()) {
            chooser.setInitialDirectory(pastaAtual);
        }
        File arquivo = chooser.showOpenDialog(janela());
        if (arquivo != null) {
            carregar(arquivo);
        }
    }

    private void carregar(File arquivo) {
        try {
            String conteudo = LeitorArquivoFonte.ler(arquivo.getAbsolutePath());
            limparResultado();
            editor.setText(conteudo);
            nomeArquivo = arquivo.getName();
            pastaAtual = arquivo.getParentFile();
            labelArquivoAtual.setText(arquivo.getName());
            mostrarFaixaInicial();
            labelStatus.setText("Arquivo carregado: " + arquivo.getName());
        } catch (IOException e) {
            labelStatus.setText("Não foi possível ler " + arquivo.getName() + ": " + e.getMessage());
        }
    }

    @FXML
    void analisar() {
        String fonte = editor.getText();
        if (fonte == null || fonte.trim().isEmpty()) {
            labelStatus.setText("Não há código para analisar");
            return;
        }
        long inicio = System.nanoTime();
        try {
            RastroAnalise novoRastro = new RastroAnalise(compilador.getTabelaSLR());
            ResultadoCompilacao novo = compilador.analisar(fonte, novoRastro);
            resultado = novo;
            rastro = novoRastro;
            textoAnalisado = fonte;
        } catch (RuntimeException e) {
            // Não deveria acontecer; se acontecer, a janela continua usável e diz o que houve.
            limparResultado();
            mostrarFaixa("faixa-erro", Icones.errado(), "Erro interno na análise",
                    e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            labelStatus.setText("Erro interno na análise");
            return;
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        preencherResultado();
        labelStatus.setText("Análise concluída em " + ms + " ms");
    }

    @FXML
    private void salvarResultado() {
        if (resultado == null) {
            return;
        }
        File destino = escolherDestino("Salvar o resultado da análise", nomeBase() + "_resultado.txt");
        if (destino == null) {
            return;
        }
        try {
            Files.write(destino.toPath(), (resultado.formatar() + "\n").getBytes(StandardCharsets.UTF_8));
            labelStatus.setText("Resultado salvo em " + destino.getName());
        } catch (IOException e) {
            labelStatus.setText("Não foi possível salvar: " + e.getMessage());
        }
    }

    @FXML
    private void salvarTokens() {
        if (resultado == null) {
            return;
        }
        String sugestao = nomeArquivo == null ? "saida.txt" : EscritorArquivoSaida.nomeDeSaidaPara(nomeArquivo);
        File destino = escolherDestino("Salvar a lista de tokens", sugestao);
        if (destino == null) {
            return;
        }
        try {
            EscritorArquivoSaida.escrever(destino.getAbsolutePath(), resultado.getTokens());
            labelStatus.setText("Lista de tokens salva em " + destino.getName());
        } catch (IOException e) {
            labelStatus.setText("Não foi possível salvar: " + e.getMessage());
        }
    }

    @FXML
    private void mostrarAjuda() {
        Alert ajuda = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        ajuda.initOwner(janela());
        ajuda.setTitle("Como usar o TokenFlow");
        ajuda.setHeaderText("Como usar o TokenFlow");
        Label texto = new Label(String.join("\n",
                "1. Digite ou cole um programa à esquerda, ou abra um .txt (Ctrl+O).",
                "2. Clique em Analisar (F5). A faixa no alto da direita diz se o programa está correto",
                "    ou quantos erros há; a aba Erros lista cada um, com a fase e a linha.",
                "3. Clique num erro, token, passo ou símbolo para ver a linha no código.",
                "",
                "Abas",
                "  Erros: léxicos, sintáticos e semânticos, em ordem de linha.",
                "  Tokens: a análise léxica (lexema e classe de cada token).",
                "  Pilha: a análise sintática SLR(1) passo a passo: pilha de estados, símbolos,",
                "    entrada que falta ler e ação. Erros e recuperações aparecem como passos.",
                "  Símbolos: a tabela de símbolos montada pela análise semântica.",
                "  Gramática e SLR(1): a gramática, FIRST e FOLLOW, os estados LR(0) e a tabela",
                "    ACTION / GOTO. O passo selecionado na aba Pilha fica destacado nela.",
                "",
                "Na tabela ACTION / GOTO: sN empilha e vai para o estado N; rN reduz pela",
                "produção N; acc aceita; célula vazia é erro sintático.",
                "",
                "Atalhos: Ctrl+O abrir, Ctrl+S salvar o resultado, F5 ou Ctrl+Enter analisar."));
        texto.setWrapText(true);
        texto.getStyleClass().add("texto-ajuda");
        ajuda.getDialogPane().setContent(texto);
        ajuda.getDialogPane().setPrefWidth(620);
        ajuda.show();
    }

    // ------------------------------------------------------------ navegação na pilha

    @FXML
    private void primeiroPasso() {
        selecionarPasso(0);
    }

    @FXML
    private void passoAnterior() {
        selecionarPasso(Math.max(0, tabelaPassos.getSelectionModel().getSelectedIndex() - 1));
    }

    @FXML
    private void proximoPasso() {
        selecionarPasso(Math.min(tabelaPassos.getItems().size() - 1,
                tabelaPassos.getSelectionModel().getSelectedIndex() + 1));
    }

    @FXML
    private void ultimoPasso() {
        selecionarPasso(tabelaPassos.getItems().size() - 1);
    }

    // Próximo passo de erro depois do selecionado (volta ao começo no fim).
    @FXML
    private void proximoErroNaPilha() {
        List<PassoAnalise> passos = tabelaPassos.getItems();
        int atual = tabelaPassos.getSelectionModel().getSelectedIndex();
        for (int k = 1; k <= passos.size(); k++) {
            int i = (atual + k) % passos.size();
            if (passos.get(i).getTipo() == PassoAnalise.Tipo.ERRO) {
                selecionarPasso(i);
                return;
            }
        }
        labelStatus.setText("Não há erros sintáticos na pilha");
    }

    @FXML
    private void verPassoNaTabela() {
        abasResultado.getSelectionModel().select(abaSLR);
        painelSLR.mostrarDestaque();
    }

    private void selecionarPasso(int indice) {
        if (indice < 0 || indice >= tabelaPassos.getItems().size()) {
            return;
        }
        tabelaPassos.getSelectionModel().clearAndSelect(indice);
        tabelaPassos.scrollTo(Math.max(0, indice - 5));
    }

    private void mostrarPasso(PassoAnalise passo) {
        int total = tabelaPassos.getItems().size();
        boolean temPassos = total > 0;
        boolean selecionado = passo != null;
        btnPrimeiroPasso.setDisable(!temPassos);
        btnUltimoPasso.setDisable(!temPassos);
        btnPassoAnterior.setDisable(!selecionado || passo.getNumero() <= 1);
        btnProximoPasso.setDisable(!temPassos || (selecionado && passo.getNumero() >= total));
        btnProximoErro.setDisable(!temPassos || resultado.errosDa(CategoriaErro.SINTATICO).isEmpty());
        btnVerNaTabela.setDisable(!selecionado);
        if (!selecionado) {
            labelPasso.setText("Passo – de " + total);
            labelDetalhePasso.setText(temPassos
                    ? "Selecione um passo (ou use as setas) para ver a pilha inteira e a célula da tabela SLR usada."
                    : "Analise um programa para ver os movimentos da pilha.");
            painelSLR.destacar(-1, null);
            return;
        }
        labelPasso.setText("Passo " + passo.getNumero() + " de " + total);
        labelDetalhePasso.setText(String.join("\n",
                "Estado no topo: " + passo.getTopo() + "    Entrada: " + descreverEntrada(passo)
                        + "    Linha: " + passo.getLinha(),
                "Pilha: " + limitarInicio(passo.pilhaComoTexto(), 300),
                "Símbolos: " + limitarInicio(passo.simbolosComoTexto(), 300),
                "Falta ler: " + RastroAnalise.entradaRestante(passo, resultado.getTokens(), LEXEMAS_NO_DETALHE),
                "Ação: " + passo.getDescricao()));
        editor.selecionarLinha(passo.getLinha());
        if (passo.getTipo() == PassoAnalise.Tipo.RECUPERACAO) {
            painelSLR.destacar(passo.getTopo(), null);
        } else {
            painelSLR.destacar(passo.getTopo(), passo.getTerminal());
        }
    }

    private static String descreverEntrada(PassoAnalise p) {
        if (p.getTerminal().equals("$")) {
            return "$ (fim do arquivo)";
        }
        String texto = p.isInserido() ? "'" + p.getTerminal() + "' (inserido)" : "'" + p.getLexema() + "'";
        return p.getLexema().equals(p.getTerminal()) || p.isInserido() ? texto : texto + " (" + p.getTerminal() + ")";
    }

    // Texto longo demais: mantém o fim (o topo da pilha), com "… " no começo.
    private static String limitarInicio(String texto, int maximo) {
        return texto.length() <= maximo ? texto : "… " + texto.substring(texto.length() - maximo);
    }

    // ------------------------------------------------------------ resultado

    private void preencherResultado() {
        ResultadoCompilacao r = resultado;
        tabelaErros.setItems(FXCollections.observableArrayList(r.getErros()));
        tabelaTokens.setItems(FXCollections.observableArrayList(r.getTokens()));
        tabelaPassos.setItems(FXCollections.observableArrayList(rastro.getPassos()));
        List<Simbolo> simbolos = new ArrayList<>();
        for (Simbolo s : r.getTabelaSimbolos().getHistorico()) {
            if (s.getCategoria() != CategoriaSimbolo.NAO_DECLARADO) {
                simbolos.add(s);
            }
        }
        tabelaSimbolos.setItems(FXCollections.observableArrayList(simbolos));

        boolean semanticaParou = !r.isSemanticaCompleta();
        labelAvisoSimbolos.setText("A análise semântica parou no primeiro erro sintático: a tabela tem só o que "
                + "foi declarado até ali.");
        labelAvisoSimbolos.setVisible(semanticaParou);
        labelAvisoSimbolos.setManaged(semanticaParou);

        Set<Integer> linhasComErro = new TreeSet<>();
        for (ErroCompilacao e : r.getErros()) {
            linhasComErro.add(e.getLinha());
        }
        editor.marcarLinhasComErro(linhasComErro);

        int passos = rastro.getPassos().size();
        abaErros.setText(r.getErros().isEmpty() ? "Erros" : "Erros (" + r.getErros().size() + ")");
        abaTokens.setText("Tokens (" + r.getTokens().size() + ")");
        abaPilha.setText("Pilha (" + passos + (rastro.isTruncado() ? "+" : "") + ")");
        abaSimbolos.setText("Símbolos (" + simbolos.size() + ")");
        labelContagemTokens.setText("Tokens: " + r.getTokens().size());
        labelContagemErros.setText("Erros: " + r.getErros().size());
        labelContagemPassos.setText("Passos: " + passos + (rastro.isTruncado() ? " (rastro limitado)" : ""));
        tabelaErros.setPlaceholder(new Label("Nenhum erro: o programa está correto."));
        btnSalvar.setDisable(false);
        btnSalvarTokens.setDisable(false);
        tabelaPassos.getSelectionModel().clearSelection();
        mostrarPasso(null);
        mostrarFaixaDoResultado();
    }

    private void limparResultado() {
        resultado = null;
        rastro = null;
        textoAnalisado = null;
        tabelaErros.setItems(FXCollections.<ErroCompilacao>observableArrayList());
        tabelaTokens.setItems(FXCollections.<Token>observableArrayList());
        tabelaPassos.setItems(FXCollections.<PassoAnalise>observableArrayList());
        tabelaSimbolos.setItems(FXCollections.<Simbolo>observableArrayList());
        tabelaErros.setPlaceholder(new Label("Analise um programa para ver os erros."));
        tabelaTokens.setPlaceholder(new Label("Analise um programa para ver os tokens."));
        tabelaPassos.setPlaceholder(new Label("Analise um programa para ver os movimentos da pilha."));
        tabelaSimbolos.setPlaceholder(new Label("Analise um programa para ver a tabela de símbolos."));
        labelAvisoSimbolos.setVisible(false);
        labelAvisoSimbolos.setManaged(false);
        editor.marcarLinhasComErro(new TreeSet<Integer>());
        abaErros.setText("Erros");
        abaTokens.setText("Tokens");
        abaPilha.setText("Pilha");
        abaSimbolos.setText("Símbolos");
        labelContagemTokens.setText("Tokens: 0");
        labelContagemErros.setText("Erros: 0");
        labelContagemPassos.setText("Passos: 0");
        btnSalvar.setDisable(true);
        btnSalvarTokens.setDisable(true);
        mostrarPasso(null);
    }

    private void textoMudou(String novo) {
        btnAnalisar.setDisable(novo == null || novo.trim().isEmpty());
        if (resultado == null) {
            return;
        }
        if (novo != null && novo.equals(textoAnalisado)) {
            mostrarFaixaDoResultado();
        } else {
            mostrarFaixa("faixa-aviso", Icones.atencao(), "O código mudou desde a última análise",
                    "Os resultados abaixo são do código anterior. Clique em Analisar (F5) para atualizar.");
        }
    }

    private void mostrarFaixaInicial() {
        mostrarFaixa("faixa-neutra", Icones.info(), "Nenhuma análise ainda",
                "Digite ou abra um programa e clique em Analisar (F5).");
    }

    private void mostrarFaixaDoResultado() {
        ResultadoCompilacao r = resultado;
        if (r.isSucesso()) {
            mostrarFaixa("faixa-ok", Icones.certo(), "OK: o programa está correto",
                    "Sem erros léxicos, sintáticos ou semânticos.");
            return;
        }
        int lexicos = r.errosDa(CategoriaErro.LEXICO).size();
        int sintaticos = r.errosDa(CategoriaErro.SINTATICO).size();
        int semanticos = r.errosDa(CategoriaErro.SEMANTICO).size();
        List<String> partes = new ArrayList<>();
        if (lexicos > 0) {
            partes.add(quantos(lexicos, "léxico", "léxicos"));
        }
        if (sintaticos > 0) {
            partes.add(quantos(sintaticos, "sintático", "sintáticos"));
        }
        if (semanticos > 0) {
            partes.add(quantos(semanticos, "semântico", "semânticos"));
        }
        String detalhe = juntarComE(partes) + ".";
        if (!r.isSemanticaCompleta()) {
            detalhe += " A análise semântica parou no primeiro erro sintático.";
        }
        int total = r.getErros().size();
        mostrarFaixa("faixa-erro", Icones.errado(), total == 1 ? "1 erro encontrado" : total + " erros encontrados",
                detalhe);
    }

    private void mostrarFaixa(String estilo, Node icone, String titulo, String detalhe) {
        faixaResultado.getStyleClass().removeAll("faixa-neutra", "faixa-ok", "faixa-erro", "faixa-aviso");
        faixaResultado.getStyleClass().add(estilo);
        iconeResultado.getChildren().setAll(icone);
        labelResultado.setText(titulo);
        labelDetalheResultado.setText(detalhe);
    }

    // ------------------------------------------------------------------ apoio

    private static String fase(CategoriaErro c) {
        switch (c) {
            case LEXICO:
                return "Léxico";
            case SINTATICO:
                return "Sintático";
            default:
                return "Semântico";
        }
    }

    private static String quantos(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    private static String juntarComE(List<String> partes) {
        if (partes.size() <= 1) {
            return partes.isEmpty() ? "" : capitalizar(partes.get(0));
        }
        return capitalizar(String.join(", ", partes.subList(0, partes.size() - 1)) + " e " + partes.get(partes.size() - 1));
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Linha de tabela que recebe uma classe de estilo conforme o item (null: nenhuma).
    private static <T> TableRow<T> linhaComClasse(Function<T, String> classe) {
        return new TableRow<T>() {
            private String aplicada;

            @Override
            protected void updateItem(T item, boolean vazio) {
                super.updateItem(item, vazio);
                if (aplicada != null) {
                    getStyleClass().remove(aplicada);
                    aplicada = null;
                }
                if (!vazio && item != null) {
                    aplicada = classe.apply(item);
                    if (aplicada != null) {
                        getStyleClass().add(aplicada);
                    }
                }
            }
        };
    }

    // Célula de texto com o corte escolhido e o texto inteiro na dica.
    private static <T> TableCell<T, String> celulaComDica(OverrunStyle corte) {
        TableCell<T, String> celula = new TableCell<T, String>() {
            @Override
            protected void updateItem(String texto, boolean vazio) {
                super.updateItem(texto, vazio);
                setText(vazio ? null : texto);
                setTooltip(vazio || texto == null || texto.length() < 25 ? null : new Tooltip(texto));
            }
        };
        celula.setTextOverrun(corte);
        return celula;
    }

    private File escolherDestino(String titulo, String sugestao) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(titulo);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos de texto (*.txt)", "*.txt"));
        chooser.setInitialFileName(sugestao);
        if (pastaAtual != null && pastaAtual.isDirectory()) {
            chooser.setInitialDirectory(pastaAtual);
        }
        File destino = chooser.showSaveDialog(janela());
        if (destino != null) {
            pastaAtual = destino.getParentFile();
        }
        return destino;
    }

    private String nomeBase() {
        if (nomeArquivo == null) {
            return "programa";
        }
        int ponto = nomeArquivo.lastIndexOf('.');
        return ponto > 0 ? nomeArquivo.substring(0, ponto) : nomeArquivo;
    }

    private javafx.stage.Window janela() {
        return raiz.getScene() == null ? null : raiz.getScene().getWindow();
    }

}
