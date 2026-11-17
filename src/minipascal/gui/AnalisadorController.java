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
import javafx.scene.control.ButtonBar;
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
 * Controlador da janela (Main.fxml). Liga o editor, a analise completa
 * (Compilador: lexica, sintatica e semantica, com o rastro da pilha) e as abas.
 *
 * Decisoes de interface:
 *   - o codigo fica sempre visivel a esquerda; o resultado, a direita;
 *   - a faixa de resultado responde a pergunta principal (o programa esta
 *     certo?) e avisa quando o codigo mudou depois da ultima analise;
 *   - as abas seguem as fases do compilador: Erros, Tokens (lexica), Pilha
 *     (sintatica), Simbolos (semantica) e a descricao da linguagem (Gramatica
 *     e SLR(1)); o titulo de cada aba mostra quantos itens ela tem;
 *   - selecionar um erro, um token, um passo ou um simbolo mostra a linha no
 *     codigo; selecionar um passo tambem destaca a celula da tabela SLR(1)
 *     que o analisador usou;
 *   - o codigo do editor pode ser salvo; Abrir e fechar a janela perguntam
 *     antes de descartar alteracoes nao salvas;
 *   - salvar o resultado ou os tokens analisa de novo se o codigo mudou, e
 *     nao deixa gravar por cima do arquivo do programa;
 *   - atalhos: Ctrl+O abre, Ctrl+Shift+S salva o codigo, Ctrl+S salva o
 *     resultado, F5 ou Ctrl+Enter analisa.
 */
public class AnalisadorController implements Initializable {

    // Componentes declarados em Main.fxml (o fx:id de cada um e o nome do campo).
    @FXML private BorderPane raiz;
    @FXML private Button btnAbrir;
    @FXML private Button btnSalvarCodigo;
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

    // Quantos lexemas a coluna Entrada mostra (o resto vira "..."; o detalhe do passo mostra mais).
    private static final int LEXEMAS_NA_ENTRADA = 8;
    private static final int LEXEMAS_NO_DETALHE = 40;

    // Montado uma vez: as tabelas SLR(1) ficam prontas para todas as analises.
    private final Compilador compilador = new Compilador();
    private PainelSLR painelSLR;

    // Resultado da ultima analise (null antes da primeira) e o texto analisado,
    // para saber se o codigo mudou depois dela.
    private ResultadoCompilacao resultado;
    private RastroAnalise rastro;
    private String textoAnalisado;
    // Arquivo aberto ou salvo por ultimo (null: programa sem nome) e o texto
    // dele, para saber se ha alteracoes nao salvas.
    private File arquivoAtual;
    private String textoSalvo = "";
    private String nomeArquivo;
    private File pastaAtual;

    @Override
    // Chamado pelo FXMLLoader depois de ligar os campos @FXML: monta o que o FXML
    // nao descreve (painel SLR, colunas, icones, atalhos) e deixa a janela pronta.
    public void initialize(URL location, ResourceBundle resources) {
        painelSLR = new PainelSLR(compilador.getTabelaSLR());
        abaSLR.setContent(painelSLR.getConteudo());

        Fontes.usarMonoespacada(tabelaTokens, tabelaPassos, labelDetalhePasso);
        // A ultima coluna de cada tabela ocupa o espaco que sobra (sem barra de
        // rolagem horizontal enquanto couber); o texto que nao cabe termina em
        // "..." e aparece inteiro na dica.
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

        // Cada mudanca no texto atualiza o botao Analisar e a faixa de resultado.
        editor.textProperty().addListener((obs, antigo, novo) -> textoMudou(novo));
        // Os atalhos ficam na cena, que so existe depois que a janela e montada.
        raiz.sceneProperty().addListener((obs, antiga, nova) -> {
            if (nova != null) {
                configurarAtalhos(nova);
                // Fechar a janela com codigo nao salvo tambem pergunta antes.
                nova.windowProperty().addListener((o, antes, janela) -> {
                    if (janela != null) {
                        janela.setOnCloseRequest(e -> {
                            if (!podeDescartarAlteracoes()) {
                                e.consume();
                            }
                        });
                    }
                });
            }
        });
        limparResultado();
        mostrarFaixaInicial();
        // O cursor comeca no editor: da para digitar o programa logo ao abrir.
        javafx.application.Platform.runLater(() -> editor.getArea().requestFocus());
    }

    // ------------------------------------------------------------ montagem

    // Icones e dicas dos botoes.
    private void configurarBotoes() {
        btnAbrir.setGraphic(Icones.pasta());
        btnSalvarCodigo.setGraphic(Icones.disco());
        btnSalvar.setGraphic(Icones.disco());
        btnAnalisar.setGraphic(Icones.executar());
        btnPrimeiroPasso.setGraphic(Icones.primeiro());
        btnPassoAnterior.setGraphic(Icones.anterior());
        btnProximoPasso.setGraphic(Icones.proximo());
        btnUltimoPasso.setGraphic(Icones.ultimo());
        btnAbrir.setTooltip(new Tooltip("Abrir um programa (.txt)  —  Ctrl+O"));
        btnSalvarCodigo.setTooltip(new Tooltip("Salvar o código do editor num .txt  —  Ctrl+Shift+S"));
        btnSalvar.setTooltip(new Tooltip("Salvar o resultado da análise (OK ou os erros) num .txt  —  Ctrl+S\n"
                + "Se o código mudou desde a última análise, ele é analisado de novo antes."));
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

    // Ctrl+O, Ctrl+Shift+S, Ctrl+S, F5 e Ctrl+Enter, valendo em qualquer parte da janela.
    private void configurarAtalhos(Scene cena) {
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::abrirArquivo);
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), this::salvarCodigoDoEditor);
        cena.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!btnSalvar.isDisabled()) {
                salvarResultado();
            }
        });
        // So analisa se o botao estiver habilitado (ha texto no editor).
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

    // Aba Erros: linha, fase e mensagem; a cor da linha depende da fase.
    private void configurarTabelaErros() {
        colErroLinha.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getLinha()));
        colErroFase.setCellValueFactory(d -> new SimpleStringProperty(fase(d.getValue().getCategoria())));
        colErroMensagem.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getMensagem()));
        colErroMensagem.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        tabelaErros.setRowFactory(t -> linhaComClasse(e -> "erro-" + e.getCategoria().name().toLowerCase()));
        // Clicar num erro seleciona a linha dele no codigo.
        tabelaErros.getSelectionModel().selectedItemProperty().addListener((obs, antigo, erro) -> {
            if (erro != null) {
                editor.selecionarLinha(erro.getLinha());
            }
        });
    }

    // Aba Tokens: a saida da analise lexica; erros lexicos em destaque.
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

    // Aba Pilha: um passo do SLR(1) por linha (ver RastroAnalise).
    private void configurarTabelaPassos() {
        colPassoNumero.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getNumero()));
        colPassoPilha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().pilhaComoTexto()));
        colPassoSimbolos.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().simbolosComoTexto()));
        colPassoEntrada.setCellValueFactory(d -> new SimpleStringProperty(
                RastroAnalise.entradaRestante(d.getValue(), resultado.getTokens(), LEXEMAS_NA_ENTRADA)));
        colPassoAcao.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescricao()));
        // Pilha e simbolos crescem para a direita: quando nao cabem, o que se
        // corta e a base (a esquerda), e o topo continua visivel.
        colPassoPilha.setCellFactory(c -> celulaComDica(OverrunStyle.LEADING_ELLIPSIS));
        colPassoSimbolos.setCellFactory(c -> celulaComDica(OverrunStyle.LEADING_ELLIPSIS));
        colPassoEntrada.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        colPassoAcao.setCellFactory(c -> celulaComDica(OverrunStyle.ELLIPSIS));
        tabelaPassos.setRowFactory(t -> linhaComClasse(p -> "passo-" + p.getTipo().name().toLowerCase()));
        // Selecionar um passo mostra o detalhe, a linha no codigo e a celula da tabela.
        tabelaPassos.getSelectionModel().selectedItemProperty().addListener((obs, antigo, passo) -> mostrarPasso(passo));
    }

    // Aba Simbolos: a tabela de simbolos montada pela analise semantica.
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
            // os predefinidos (linha 0) nao tem linha no codigo
            if (s != null && s.getLinha() > 0) {
                editor.selecionarLinha(s.getLinha());
            }
        });
    }

    // --------------------------------------------------------------- acoes

    @FXML
    // Botao Abrir (Ctrl+O): escolhe um .txt e carrega no editor. Se o codigo
    // do editor foi alterado e nao foi salvo, pergunta antes de descarta-lo.
    private void abrirArquivo() {
        if (!podeDescartarAlteracoes()) {
            return;
        }
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

    // Le o arquivo (UTF-8 ou ANSI, ver LeitorArquivoFonte) e descarta o resultado anterior.
    private void carregar(File arquivo) {
        try {
            String conteudo = LeitorArquivoFonte.ler(arquivo.getAbsolutePath());
            limparResultado();
            editor.setText(conteudo);
            definirArquivoAtual(arquivo);
            mostrarFaixaInicial();
            labelStatus.setText("Arquivo carregado: " + arquivo.getName());
        } catch (IOException e) {
            labelStatus.setText("Não foi possível ler " + arquivo.getName() + ": " + e.getMessage());
        }
    }

    @FXML
    // Botao Analisar (F5): as tres fases de uma vez, guardando o rastro da pilha.
    void analisar() {
        String fonte = editor.getText();
        if (fonte == null || fonte.trim().isEmpty()) {
            labelStatus.setText("Não há código para analisar");
            return;
        }
        long inicio = System.nanoTime();
        try {
            // O rastro e o ouvinte da analise: registra cada passo para a aba Pilha.
            RastroAnalise novoRastro = new RastroAnalise(compilador.getTabelaSLR());
            ResultadoCompilacao novo = compilador.analisar(fonte, novoRastro);
            resultado = novo;
            rastro = novoRastro;
            textoAnalisado = fonte;
        } catch (RuntimeException e) {
            // Nao deveria acontecer; se acontecer, a janela continua usavel e diz o que houve.
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
    // Botao Salvar codigo (Ctrl+Shift+S).
    private void salvarCodigoDoEditor() {
        salvarCodigo();
    }

    // Grava o codigo do editor num .txt, que passa a ser o arquivo aberto. A
    // janela sempre pergunta onde salvar, para nao sobrescrever sem querer um
    // arquivo de teste. Devolve true se salvou.
    private boolean salvarCodigo() {
        String sugestao = nomeArquivo == null ? "programa.txt" : nomeArquivo;
        File destino = escolherDestino("Salvar o código", sugestao);
        return destino != null && salvarCodigoEm(destino);
    }

    // Grava o codigo (UTF-8, com as quebras de linha do sistema) e atualiza o arquivo aberto.
    boolean salvarCodigoEm(File destino) {
        String codigo = editor.getText().replace("\n", System.lineSeparator());
        try {
            Files.write(destino.toPath(), codigo.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            labelStatus.setText("Não foi possível salvar o código: " + e.getMessage());
            return false;
        }
        definirArquivoAtual(destino);
        labelStatus.setText("Código salvo em " + destino.getName());
        return true;
    }

    @FXML
    // Salvar resultado (Ctrl+S): grava "OK" ou os erros, como a linha de comando.
    private void salvarResultado() {
        if (!resultadoEmDia()) {
            return;
        }
        File destino = escolherDestinoDeResultado("Salvar o resultado da análise", nomeBase() + "_resultado.txt");
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
    // Salva a lista lexema/classe no formato da Unidade I.
    private void salvarTokens() {
        if (!resultadoEmDia()) {
            return;
        }
        String sugestao = nomeArquivo == null ? "saida.txt" : EscritorArquivoSaida.nomeDeSaidaPara(nomeArquivo);
        File destino = escolherDestinoDeResultado("Salvar a lista de tokens", sugestao);
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
    // Botao ?: resumo de como usar a janela.
    private void mostrarAjuda() {
        Alert ajuda = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        ajuda.initOwner(janela());
        ajuda.setTitle("Como usar o TokenFlow");
        ajuda.setHeaderText("Como usar o TokenFlow");
        Label texto = new Label(String.join("\n",
                "1. Digite ou cole um programa à esquerda, ou abra um .txt (Ctrl+O). Salvar código",
                "    (Ctrl+Shift+S) grava o que está no editor.",
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
                "Caracteres de controle (invisíveis) do arquivo aparecem no editor como □.",
                "",
                "Atalhos: Ctrl+O abrir, Ctrl+Shift+S salvar o código, Ctrl+S salvar o resultado,",
                "F5 ou Ctrl+Enter analisar."));
        texto.setWrapText(true);
        texto.getStyleClass().add("texto-ajuda");
        ajuda.getDialogPane().setContent(texto);
        ajuda.getDialogPane().setPrefWidth(620);
        ajuda.show();
    }

    // ------------------------------------------------------------ navegacao na pilha

    @FXML
    // Botoes de navegacao da aba Pilha.
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

    // Proximo passo de erro depois do selecionado (volta ao comeco no fim).
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
    // Abre a aba Gramatica e SLR(1) na celula usada pelo passo selecionado.
    private void verPassoNaTabela() {
        abasResultado.getSelectionModel().select(abaSLR);
        painelSLR.mostrarDestaque();
    }

    // Seleciona o passo e rola a tabela para ele aparecer (com alguns passos antes).
    private void selecionarPasso(int indice) {
        if (indice < 0 || indice >= tabelaPassos.getItems().size()) {
            return;
        }
        tabelaPassos.getSelectionModel().clearAndSelect(indice);
        tabelaPassos.scrollTo(Math.max(0, indice - 5));
    }

    // Atualiza tudo o que depende do passo selecionado (null = nenhum).
    private void mostrarPasso(PassoAnalise passo) {
        int total = tabelaPassos.getItems().size();
        boolean temPassos = total > 0;
        boolean selecionado = passo != null;
        // Botoes habilitados conforme a posicao do passo.
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
        // Detalhe embaixo da tabela: a pilha inteira, sem cortes.
        labelPasso.setText("Passo " + passo.getNumero() + " de " + total);
        labelDetalhePasso.setText(String.join("\n",
                "Estado no topo: " + passo.getTopo() + "    Entrada: " + descreverEntrada(passo)
                        + "    Linha: " + passo.getLinha(),
                "Pilha: " + limitarInicio(passo.pilhaComoTexto(), 300),
                "Símbolos: " + limitarInicio(passo.simbolosComoTexto(), 300),
                "Falta ler: " + RastroAnalise.entradaRestante(passo, resultado.getTokens(), LEXEMAS_NO_DETALHE),
                "Ação: " + passo.getDescricao()));
        editor.selecionarLinha(passo.getLinha());
        // Na recuperacao nao ha celula consultada: destaca so a linha do estado.
        if (passo.getTipo() == PassoAnalise.Tipo.RECUPERACAO) {
            painelSLR.destacar(passo.getTopo(), null);
        } else {
            painelSLR.destacar(passo.getTopo(), passo.getTerminal());
        }
    }

    // "'x' (id)", "';'", "'then' (inserido)" ou "$ (fim do arquivo)".
    private static String descreverEntrada(PassoAnalise p) {
        if (p.getTerminal().equals("$")) {
            return "$ (fim do arquivo)";
        }
        String texto = p.isInserido() ? "'" + p.getTerminal() + "' (inserido)" : "'" + p.getLexema() + "'";
        return p.getLexema().equals(p.getTerminal()) || p.isInserido() ? texto : texto + " (" + p.getTerminal() + ")";
    }

    // Texto longo demais: mantem o fim (o topo da pilha), com "... " no comeco.
    private static String limitarInicio(String texto, int maximo) {
        return texto.length() <= maximo ? texto : "… " + texto.substring(texto.length() - maximo);
    }

    // ------------------------------------------------------------ resultado

    // Mostra o resultado da analise em todas as abas, na faixa e na barra de status.
    private void preencherResultado() {
        ResultadoCompilacao r = resultado;
        tabelaErros.setItems(FXCollections.observableArrayList(r.getErros()));
        tabelaTokens.setItems(FXCollections.observableArrayList(r.getTokens()));
        tabelaPassos.setItems(FXCollections.observableArrayList(rastro.getPassos()));
        // Os nomes nao declarados so existem para evitar mensagens repetidas: ficam de fora.
        List<Simbolo> simbolos = new ArrayList<>();
        for (Simbolo s : r.getTabelaSimbolos().getHistorico()) {
            if (s.getCategoria() != CategoriaSimbolo.NAO_DECLARADO) {
                simbolos.add(s);
            }
        }
        tabelaSimbolos.setItems(FXCollections.observableArrayList(simbolos));

        // Aviso quando a semantica parou no primeiro erro sintatico (tabela incompleta).
        boolean semanticaParou = !r.isSemanticaCompleta();
        labelAvisoSimbolos.setText("A análise semântica parou no primeiro erro sintático: a tabela tem só o que "
                + "foi declarado até ali.");
        labelAvisoSimbolos.setVisible(semanticaParou);
        labelAvisoSimbolos.setManaged(semanticaParou);

        // Marcas nas linhas com erro, na coluna de numeros do editor.
        Set<Integer> linhasComErro = new TreeSet<>();
        for (ErroCompilacao e : r.getErros()) {
            linhasComErro.add(e.getLinha());
        }
        editor.marcarLinhasComErro(linhasComErro);

        // Titulos das abas com as contagens; "+" quando o rastro foi limitado.
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

    // Volta a janela ao estado inicial (sem analise).
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

    // Codigo editado: se ficou diferente do analisado, a faixa avisa que o
    // resultado e antigo; se voltou a ser igual, mostra o resultado de novo.
    private void textoMudou(String novo) {
        btnAnalisar.setDisable(novo == null || novo.trim().isEmpty());
        atualizarNomeDoArquivo();
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

    // Faixa verde (OK) ou vermelha com quantos erros de cada fase.
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

    // Troca a cor (classe CSS), o icone e os textos da faixa de resultado.
    private void mostrarFaixa(String estilo, Node icone, String titulo, String detalhe) {
        faixaResultado.getStyleClass().removeAll("faixa-neutra", "faixa-ok", "faixa-erro", "faixa-aviso");
        faixaResultado.getStyleClass().add(estilo);
        iconeResultado.getChildren().setAll(icone);
        labelResultado.setText(titulo);
        labelDetalheResultado.setText(detalhe);
    }

    // ------------------------------------------------------------------ apoio

    // Nome da fase para a coluna Fase da aba Erros.
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

    // "1 lexico, 2 sintaticos e 3 semanticos".
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

    // Celula de texto com o corte escolhido e o texto inteiro na dica.
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

    // ------------------------------------------------ arquivo aberto e alteracoes

    // O codigo do editor esta diferente do que foi aberto ou salvo por ultimo?
    boolean temAlteracoesNaoSalvas() {
        String texto = editor.getText();
        return !(texto == null ? "" : texto).equals(textoSalvo);
    }

    // Nome do arquivo na barra de ferramentas, com o aviso de alteracoes nao salvas.
    private void atualizarNomeDoArquivo() {
        String nome = nomeArquivo == null ? "programa sem nome" : nomeArquivo;
        labelArquivoAtual.setText(temAlteracoesNaoSalvas() ? nome + " (alterado, não salvo)" : nome);
    }

    // O arquivo aberto passou a ser este: nome, pasta, texto salvo e a dica com o caminho.
    private void definirArquivoAtual(File arquivo) {
        arquivoAtual = arquivo;
        nomeArquivo = arquivo.getName();
        pastaAtual = arquivo.getParentFile();
        // O texto como ficou no editor (ver EditorCodigo.paraEditor) e o "salvo".
        textoSalvo = editor.getText();
        labelArquivoAtual.setTooltip(new Tooltip(arquivo.getAbsolutePath()));
        atualizarNomeDoArquivo();
    }

    /*
     * Antes de trocar o codigo do editor (Abrir) ou fechar a janela: se ha
     * alteracoes nao salvas, pergunta se salva, descarta ou cancela.
     * Devolve true se pode seguir.
     */
    boolean podeDescartarAlteracoes() {
        if (!temAlteracoesNaoSalvas()) {
            return true;
        }
        ButtonType salvar = new ButtonType("Salvar código", ButtonBar.ButtonData.YES);
        ButtonType descartar = new ButtonType("Descartar alterações", ButtonBar.ButtonData.NO);
        Alert pergunta = new Alert(Alert.AlertType.CONFIRMATION, "", salvar, descartar, ButtonType.CANCEL);
        pergunta.initOwner(janela());
        pergunta.setTitle("Código não salvo");
        pergunta.setHeaderText("O código foi alterado e não foi salvo");
        pergunta.setContentText("Deseja salvar o código antes de continuar?");
        ButtonType escolha = pergunta.showAndWait().orElse(ButtonType.CANCEL);
        if (escolha == salvar) {
            return salvarCodigo();
        }
        return escolha == descartar;
    }

    /*
     * Antes de salvar o resultado ou os tokens: se o codigo mudou depois da
     * ultima analise, analisa de novo, para nao gravar um resultado antigo.
     * Devolve true se ha um resultado do codigo que esta no editor.
     */
    boolean resultadoEmDia() {
        String texto = editor.getText();
        if (resultado != null && texto != null && texto.equals(textoAnalisado)) {
            return true;
        }
        analisar();
        return resultado != null && editor.getText().equals(textoAnalisado);
    }

    /*
     * Janela de salvar para o resultado e os tokens. Nao deixa escolher o
     * arquivo do programa aberto: grava-lo apagaria o codigo (a linha de
     * comando tambem recusa entrada e saida iguais).
     */
    private File escolherDestinoDeResultado(String titulo, String sugestao) {
        while (true) {
            File destino = escolherDestino(titulo, sugestao);
            if (destino == null || !ehOArquivoDoPrograma(destino)) {
                return destino;
            }
            Alert aviso = new Alert(Alert.AlertType.WARNING, "", ButtonType.OK);
            aviso.initOwner(janela());
            aviso.setTitle("Arquivo do programa");
            aviso.setHeaderText(destino.getName() + " é o arquivo do programa aberto");
            aviso.setContentText("Gravar o resultado nele apagaria o código. Escolha outro nome.");
            aviso.showAndWait();
        }
    }

    // O arquivo dado e o do programa aberto (o mesmo caminho, ou o mesmo arquivo no disco)?
    boolean ehOArquivoDoPrograma(File destino) {
        if (arquivoAtual == null || destino == null) {
            return false;
        }
        if (arquivoAtual.getAbsoluteFile().toPath().normalize()
                .equals(destino.getAbsoluteFile().toPath().normalize())) {
            return true;
        }
        try {
            return destino.exists() && arquivoAtual.exists()
                    && Files.isSameFile(arquivoAtual.toPath(), destino.toPath());
        } catch (IOException e) {
            return false;
        }
    }

    // Janela de salvar, comecando na pasta do ultimo arquivo aberto.
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
