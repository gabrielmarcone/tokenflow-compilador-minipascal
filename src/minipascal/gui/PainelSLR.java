package minipascal.gui;

import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;
import minipascal.sintatico.slr.Acao;
import minipascal.sintatico.slr.AnaliseSLR;
import minipascal.sintatico.slr.ColecaoLR0;
import minipascal.sintatico.slr.ConjuntosFirstFollow;
import minipascal.sintatico.slr.EstadoLR0;
import minipascal.sintatico.slr.ItemLR0;
import minipascal.sintatico.slr.TabelaSLR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A aba "Gramatica e SLR(1)": tudo o que o gerador de tabelas calculou a
 * partir de gramatica.txt, em quatro partes.
 *
 *   - Tabela ACTION / GOTO: uma linha por estado; as colunas de ACTION sao os
 *     terminais e as de GOTO, os nao terminais. A celula usada no passo
 *     selecionado na aba Pilha fica destacada (destacar).
 *   - Gramatica: as producoes numeradas (o numero e o do "rN").
 *   - FIRST e FOLLOW: dos nao terminais, e quais sao anulaveis.
 *   - Estados LR(0): os itens de cada estado e as suas transicoes.
 *
 * Nada aqui depende do programa analisado: e a descricao da linguagem.
 */
final class PainelSLR {

    // Quadros da tela em que a rolagem ate a celula destacada e repetida (ver mostrarDestaque).
    private static final int QUADROS_DE_ROLAGEM = 3;

    private final TabelaSLR tabela;
    private final Gramatica gramatica;
    private final TabPane abas = new TabPane();
    private final TableView<Integer> tabelaAcoes = new TableView<>();
    private final ListView<Integer> listaEstados = new ListView<>();
    // Diz qual celula esta destacada: ao rolar ate ela, a coluna Estado pode sair de vista.
    private final Label labelDestaque = new Label();
    private final Tab abaAcoes;
    private final Tab abaEstados;
    private int estadoDestacado = -1;
    private String simboloDestacado = null;
    private AnimationTimer rolagemPendente;

    PainelSLR(TabelaSLR tabela) {
        this.tabela = tabela;
        this.gramatica = tabela.getGramatica();
        // Quatro abas internas, uma para cada parte da construcao SLR(1).
        abas.setId("abasSLR");
        abas.getStyleClass().add("abas-internas");
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abaAcoes = new Tab("Tabela ACTION / GOTO", montarTabelaAcoes());
        abaEstados = new Tab("Estados LR(0)", montarEstados());
        abas.getTabs().addAll(abaAcoes, new Tab("Gramática", montarGramatica()),
                new Tab("FIRST e FOLLOW", montarFirstFollow()), abaEstados);
        abaAcoes.setTooltip(new Tooltip("O que o analisador faz em cada estado com cada símbolo"));
    }

    TabPane getConteudo() {
        return abas;
    }

    TableView<Integer> getTabelaAcoes() {
        return tabelaAcoes;
    }

    /**
     * Destaca a celula [estado, simbolo] e a linha do estado (simbolo null
     * destaca so a linha). Estado -1 tira o destaque.
     */
    void destacar(int estado, String simbolo) {
        estadoDestacado = estado;
        simboloDestacado = simbolo;
        // redesenha as celulas visiveis com o destaque novo
        tabelaAcoes.refresh();
        boolean visivel = estado >= 0;
        // Frase que diz qual celula esta destacada e o que ela significa.
        labelDestaque.setVisible(visivel);
        labelDestaque.setManaged(visivel);
        if (!visivel) {
            labelDestaque.setText("");
        } else if (simbolo == null) {
            labelDestaque.setText("Passo selecionado: estado " + estado + " (recuperação de erro)");
        } else {
            boolean terminal = gramatica.ehTerminal(simbolo);
            String texto = celula(estado, simbolo, terminal);
            labelDestaque.setText("Passo selecionado: estado " + estado + ", " + simbolo + " → "
                    + (texto.isEmpty() ? "vazio (erro sintático)" : texto + " (" + explicar(estado, simbolo, texto, terminal)
                    .replaceFirst("^[^:]*: ", "") + ")"));
        }
    }

    /*
     * Abre a tabela ACTION / GOTO e rola ate a celula destacada.
     *
     * Uma tabela que acaba de aparecer (a aba estava fechada) ainda nao foi
     * medida e ignora a rolagem, ficando onde estava. Por isso a rolagem e
     * repetida nos proximos quadros da tela, depois que a tabela e desenhada.
     */
    void mostrarDestaque() {
        abas.getSelectionModel().select(abaAcoes);
        if (estadoDestacado < 0) {
            return;
        }
        rolarAteDestaque();
        if (rolagemPendente != null) {
            rolagemPendente.stop();
        }
        rolagemPendente = new AnimationTimer() {
            private int quadros = 0;

            @Override
            public void handle(long agora) {
                rolarAteDestaque();
                if (++quadros >= QUADROS_DE_ROLAGEM) {
                    stop();
                }
            }
        };
        rolagemPendente.start();
    }

    // Rola a tabela ate o estado (com 3 linhas antes) e a coluna do simbolo.
    private void rolarAteDestaque() {
        if (estadoDestacado < 0) {
            return;
        }
        tabelaAcoes.scrollTo(Math.max(0, estadoDestacado - 3));
        tabelaAcoes.getSelectionModel().select(estadoDestacado);
        TableColumn<Integer, ?> coluna = colunaDe(simboloDestacado);
        if (coluna == null) {
            return;
        }
        // Se a celula cabe na tela com a coluna Estado, mostra as duas; senao, rola ate a celula.
        double inicio = 0;
        for (TableColumn<Integer, ?> c : tabelaAcoes.getVisibleLeafColumns()) {
            if (c == coluna) {
                break;
            }
            inicio += c.getWidth();
        }
        if (inicio + coluna.getWidth() <= tabelaAcoes.getWidth() - 30) {
            tabelaAcoes.scrollToColumnIndex(0);
        } else {
            tabelaAcoes.scrollToColumn(coluna);
        }
    }

    // ------------------------------------------------------- ACTION / GOTO

    // Uma linha por estado. Colunas: Estado, o grupo ACTION (um terminal por
    // coluna) e o grupo GOTO (um nao terminal por coluna).
    private VBox montarTabelaAcoes() {
        tabelaAcoes.setId("tabelaSLR");
        Fontes.usarMonoespacada(tabelaAcoes);
        tabelaAcoes.getStyleClass().add("tabela-slr");
        // altura fixa: rolagem rapida com 120 linhas
        tabelaAcoes.setFixedCellSize(24);

        TableColumn<Integer, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue())));
        colEstado.setPrefWidth(62);
        colEstado.setSortable(false);
        colEstado.getStyleClass().add("coluna-estado");

        TableColumn<Integer, String> action = new TableColumn<>("ACTION");
        for (String t : gramatica.getTerminais()) {
            action.getColumns().add(colunaDaTabela(t, true));
        }
        TableColumn<Integer, String> goTo = new TableColumn<>("GOTO");
        for (String n : gramatica.getNaoTerminais()) {
            goTo.getColumns().add(colunaDaTabela(n, false));
        }
        action.setSortable(false);
        goTo.setSortable(false);
        action.getStyleClass().add("grupo-slr");
        goTo.getStyleClass().add("grupo-slr");
        tabelaAcoes.getColumns().add(colEstado);
        tabelaAcoes.getColumns().add(action);
        tabelaAcoes.getColumns().add(goTo);
        // Os itens da tabela sao os numeros dos estados; as celulas sao calculadas.
        for (int e = 0; e < tabela.numeroDeEstados(); e++) {
            tabelaAcoes.getItems().add(e);
        }
        // Linha do estado destacado com fundo proprio.
        tabelaAcoes.setRowFactory(t -> new TableRow<Integer>() {
            @Override
            protected void updateItem(Integer estado, boolean vazio) {
                super.updateItem(estado, vazio);
                getStyleClass().remove("linha-destacada");
                if (!vazio && estado != null && estado == estadoDestacado) {
                    getStyleClass().add("linha-destacada");
                }
            }
        });
        VBox.setVgrow(tabelaAcoes, Priority.ALWAYS);

        Label legenda = new Label("sN: empilha e vai para o estado N    rN: reduz pela produção N (aba Gramática)    "
                + "acc: aceita    vazio: erro sintático    GOTO: estado depois de reduzir para o não terminal");
        legenda.getStyleClass().add("legenda");
        legenda.setWrapText(true);
        legenda.setMinHeight(Region.USE_PREF_SIZE);

        // Campo "Ir para o estado": numero invalido deixa o campo vermelho.
        TextField irPara = new TextField();
        irPara.setId("campoIrParaEstado");
        irPara.setPromptText("nº");
        irPara.setPrefColumnCount(4);
        irPara.setOnAction(e -> {
            try {
                int estado = Integer.parseInt(irPara.getText().trim());
                if (estado >= 0 && estado < tabela.numeroDeEstados()) {
                    tabelaAcoes.scrollTo(Math.max(0, estado - 3));
                    tabelaAcoes.getSelectionModel().select(estado);
                    irPara.getStyleClass().remove("campo-invalido");
                    return;
                }
            } catch (NumberFormatException ignorada) {
                // cai no aviso abaixo
            }
            if (!irPara.getStyleClass().contains("campo-invalido")) {
                irPara.getStyleClass().add("campo-invalido");
            }
        });
        irPara.setTooltip(new Tooltip("Digite o número do estado (0 a " + (tabela.numeroDeEstados() - 1)
                + ") e tecle Enter"));
        Label rotuloIr = new Label("Ir para o estado:");
        Label resumo = new Label(tabela.numeroDeEstados() + " estados, " + (gramatica.getTerminais().size() - 1)
                + " terminais (mais o $), " + gramatica.getNaoTerminais().size() + " não terminais, nenhum conflito");
        resumo.getStyleClass().add("legenda");
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox barra = new HBox(8, resumo, espaco, rotuloIr, irPara);
        barra.setAlignment(Pos.CENTER_LEFT);
        labelDestaque.setId("labelDestaqueSLR");
        labelDestaque.getStyleClass().add("destaque-slr");
        labelDestaque.setWrapText(true);
        labelDestaque.setMaxWidth(Double.MAX_VALUE);
        labelDestaque.setMinHeight(Region.USE_PREF_SIZE);
        labelDestaque.setVisible(false);
        labelDestaque.setManaged(false);

        VBox caixa = new VBox(6, barra, labelDestaque, tabelaAcoes, legenda);
        caixa.setPadding(new Insets(8, 0, 0, 0));
        return caixa;
    }

    // Uma coluna da tabela: a celula mostra sN, rN, acc (ACTION) ou o estado (GOTO),
    // com cor por tipo de acao e a explicacao na dica.
    private TableColumn<Integer, String> colunaDaTabela(String simbolo, boolean terminal) {
        TableColumn<Integer, String> c = new TableColumn<>(simbolo);
        c.setSortable(false);
        // o simbolo fica na coluna, para achar a coluna de um passo (colunaDe)
        c.setUserData(simbolo);
        c.setPrefWidth(Math.max(42, 9 * simbolo.length() + 18));
        c.setCellValueFactory(d -> new SimpleStringProperty(celula(d.getValue(), simbolo, terminal)));
        c.setCellFactory(col -> new TableCell<Integer, String>() {
            // No JavaFX 8, getTableRow() devolve TableRow sem tipo: o cast e necessario la.
            @Override
            @SuppressWarnings("cast")
            protected void updateItem(String texto, boolean vazio) {
                super.updateItem(texto, vazio);
                // a celula e reaproveitada pela tabela: limpa as classes do uso anterior
                getStyleClass().removeAll("celula-destacada", "celula-reduzir", "celula-empilhar", "celula-aceitar");
                setTooltip(null);
                if (vazio || texto == null) {
                    setText(null);
                    return;
                }
                setText(texto);
                Integer estado = (Integer) getTableRow().getItem();
                if (estado == null) {
                    return;
                }
                if (texto.startsWith("r")) {
                    getStyleClass().add("celula-reduzir");
                } else if (texto.startsWith("s")) {
                    getStyleClass().add("celula-empilhar");
                } else if (texto.equals("acc")) {
                    getStyleClass().add("celula-aceitar");
                }
                if (estado == estadoDestacado && simbolo.equals(simboloDestacado)) {
                    getStyleClass().add("celula-destacada");
                }
                if (!texto.isEmpty()) {
                    setTooltip(new Tooltip(explicar(estado, simbolo, texto, terminal)));
                }
            }
        });
        return c;
    }

    // Texto da celula [estado, simbolo]: ACTION para terminal, GOTO para nao terminal.
    private String celula(int estado, String simbolo, boolean terminal) {
        if (terminal) {
            Acao a = tabela.acao(estado, simbolo);
            return a == null ? "" : a.toString();
        }
        int destino = tabela.desvio(estado, simbolo);
        return destino < 0 ? "" : String.valueOf(destino);
    }

    // Explicacao da celula em portugues (dica e frase do destaque).
    private String explicar(int estado, String simbolo, String texto, boolean terminal) {
        String inicio = "Estado " + estado + ", " + (terminal ? "terminal " : "não terminal ") + simbolo + ": ";
        if (!terminal) {
            return inicio + "depois de reduzir para " + simbolo + ", vai para o estado " + texto;
        }
        if (texto.equals("acc")) {
            return inicio + "aceita (fim da análise)";
        }
        int n = Integer.parseInt(texto.substring(1));
        return texto.startsWith("s") ? inicio + "empilha e vai para o estado " + n
                : inicio + "reduz pela produção " + n + ": " + gramatica.getProducao(n);
    }

    // A coluna (dentro de ACTION ou GOTO) de um simbolo.
    private TableColumn<Integer, ?> colunaDe(String simbolo) {
        if (simbolo == null) {
            return null;
        }
        for (TableColumn<Integer, ?> grupo : tabelaAcoes.getColumns()) {
            for (TableColumn<Integer, ?> c : grupo.getColumns()) {
                if (simbolo.equals(c.getUserData())) {
                    return c;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------- gramatica

    // Aba Gramatica: as producoes numeradas, como nos rN da tabela.
    private VBox montarGramatica() {
        TableView<Producao> t = new TableView<>();
        t.setId("tabelaGramatica");
        Fontes.usarMonoespacada(t);
        TableColumn<Producao, String> num = new TableColumn<>("Nº");
        num.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getNumero())));
        num.setPrefWidth(50);
        num.setSortable(false);
        TableColumn<Producao, String> prod = new TableColumn<>("Produção");
        prod.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().toString()));
        prod.setPrefWidth(560);
        prod.setSortable(false);
        t.getColumns().add(num);
        t.getColumns().add(prod);
        Colunas.ocuparEspacoQueSobra(t, prod, 300);
        t.getItems().addAll(gramatica.getProducoes());
        VBox.setVgrow(t, Priority.ALWAYS);
        Label nota = new Label((gramatica.getProducoes().size() - 1) + " produções; a 0 é a produção inicial aumentada "
                + "(S' -> PROG). ε é a produção vazia.");
        nota.getStyleClass().add("legenda");
        nota.setWrapText(true);
        nota.setMinHeight(Region.USE_PREF_SIZE);
        VBox caixa = new VBox(6, t, nota);
        caixa.setPadding(new Insets(8, 0, 0, 0));
        return caixa;
    }

    // ------------------------------------------------------- FIRST e FOLLOW

    // Aba FIRST e FOLLOW: uma linha por nao terminal.
    private VBox montarFirstFollow() {
        AnaliseSLR analise = tabela.getAnalise();
        ConjuntosFirstFollow conj = analise.getConjuntos();
        TableView<String> t = new TableView<>();
        t.setId("tabelaFirstFollow");
        Fontes.usarMonoespacada(t);
        TableColumn<String, String> nt = new TableColumn<>("Não terminal");
        nt.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()));
        nt.setPrefWidth(130);
        TableColumn<String, String> anulavel = new TableColumn<>("Anulável");
        anulavel.setCellValueFactory(d -> new SimpleStringProperty(conj.ehAnulavel(d.getValue()) ? "sim" : ""));
        anulavel.setPrefWidth(76);
        TableColumn<String, String> first = new TableColumn<>("FIRST");
        first.setCellValueFactory(d -> new SimpleStringProperty(conjunto(conj.first(d.getValue()))));
        first.setPrefWidth(300);
        TableColumn<String, String> follow = new TableColumn<>("FOLLOW");
        follow.setCellValueFactory(d -> new SimpleStringProperty(conjunto(conj.follow(d.getValue()))));
        follow.setPrefWidth(420);
        for (TableColumn<String, String> c : java.util.Arrays.asList(nt, anulavel, first, follow)) {
            c.setSortable(false);
            t.getColumns().add(c);
        }
        // Os conjuntos podem ser longos: quebram em mais de uma linha em vez de serem cortados.
        first.setCellFactory(col -> Colunas.celulaQueQuebra());
        follow.setCellFactory(col -> Colunas.celulaQueQuebra());
        Colunas.repartirEspacoQueSobra(t, 160, first, follow);
        t.getItems().addAll(gramatica.getNaoTerminais());
        VBox.setVgrow(t, Priority.ALWAYS);
        Label nota = new Label("FIRST(A): terminais que podem começar A.  FOLLOW(A): terminais que podem vir logo "
                + "depois de A; no SLR(1), A -> α é reduzida quando o próximo terminal está em FOLLOW(A).");
        nota.getStyleClass().add("legenda");
        nota.setWrapText(true);
        nota.setMinHeight(Region.USE_PREF_SIZE);
        VBox caixa = new VBox(6, t, nota);
        caixa.setPadding(new Insets(8, 0, 0, 0));
        return caixa;
    }

    private String conjunto(Set<String> simbolos) {
        return "{ " + String.join(" ", gramatica.ordenar(simbolos)) + " }";
    }

    // ----------------------------------------------------------- estados LR(0)

    // Aba Estados LR(0): cada estado com os itens e as transicoes.
    private VBox montarEstados() {
        ColecaoLR0 colecao = tabela.getAnalise().getColecao();
        listaEstados.setId("listaEstados");
        Fontes.usarMonoespacada(listaEstados);
        listaEstados.getStyleClass().add("lista-estados");
        for (EstadoLR0 e : colecao.getEstados()) {
            listaEstados.getItems().add(e.getNumero());
        }
        listaEstados.setCellFactory(l -> new ListCell<Integer>() {
            @Override
            protected void updateItem(Integer estado, boolean vazio) {
                super.updateItem(estado, vazio);
                setText(vazio || estado == null ? null : descreverEstado(colecao, estado));
            }
        });
        VBox.setVgrow(listaEstados, Priority.ALWAYS);
        Label nota = new Label("Item A -> α • β: α já foi reconhecido; o ponto marca onde a análise está. "
                + "Item completo (ponto no fim): reduz por A -> αβ.");
        nota.getStyleClass().add("legenda");
        nota.setWrapText(true);
        nota.setMinHeight(Region.USE_PREF_SIZE);
        VBox caixa = new VBox(6, listaEstados, nota);
        caixa.setPadding(new Insets(8, 0, 0, 0));
        return caixa;
    }

    // "I5", os itens (A -> alfa . beta) e as transicoes do estado.
    private String descreverEstado(ColecaoLR0 colecao, int estado) {
        StringBuilder sb = new StringBuilder("I" + estado);
        for (ItemLR0 item : colecao.getEstados().get(estado).getItens()) {
            sb.append("\n    ").append(item.descrever(gramatica));
        }
        Map<String, Integer> trans = colecao.transicoesDe(estado);
        if (!trans.isEmpty()) {
            List<String> partes = new ArrayList<>();
            for (String s : gramatica.ordenar(trans.keySet())) {
                partes.add(s + " → I" + trans.get(s));
            }
            sb.append("\n    transições: ").append(String.join(", ", partes));
        }
        return sb.toString();
    }

    // Abre os estados LR(0) no estado dado.
    void mostrarEstado(int estado) {
        abas.getSelectionModel().select(abaEstados);
        listaEstados.scrollTo(estado);
        listaEstados.getSelectionModel().select(estado);
    }
}
