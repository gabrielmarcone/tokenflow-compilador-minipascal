package minipascal.sintatico.verificacao;

import minipascal.cli.Console;
import minipascal.erros.CategoriaErro;
import minipascal.erros.ErroCompilacao;
import minipascal.lexico.core.AnalisadorLexico;
import minipascal.lexico.core.DescricaoErroLexico;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;
import minipascal.sintatico.parser.AdaptadorLexico;
import minipascal.sintatico.parser.Terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verifica a traducao de tokens do analisador lexico para terminais da
 * gramatica, feita pelo AdaptadorLexico. Sem JUnit, no mesmo formato das
 * verificacoes da Unidade I.
 */
public class VerificacaoAdaptador {

    private static int total = 0;
    private static int falhas = 0;
    private static final TabelaPalavrasReservadas TABELA = new TabelaPalavrasReservadas();

    public static void main(String[] args) {
        // Palavras reservadas e tipos
        checarTerminais("palavras da gramática viram o próprio terminal",
                "program var procedure begin end if then else while do div",
                "program", "var", "procedure", "begin", "end", "if", "then", "else", "while", "do", "div");
        checarTerminais("os quatro tipos viram 'tipo'",
                "integer real char string", "tipo", "tipo", "tipo", "tipo");
        checarTerminais("caixa não importa: BEGIN, Integer, MOD, Procedure",
                "BEGIN Integer MOD Procedure", "begin", "tipo", "mod", "procedure");
        checarTerminais("palavras reservadas fora da gramática mantêm o nome (for, repeat, proc)",
                "for repeat proc", "for", "repeat", "proc");

        // Identificadores e numeros
        checarTerminais("identificadores viram 'id', inclusive read/write/writeln",
                "x total read write writeln", "id", "id", "id", "id", "id");
        checarTerminais("inteiro e real viram 'numero'",
                "13 1.33 24.40e-04", "numero", "numero", "numero");

        // Operadores e simbolos
        checarTerminais("operadores aritméticos e relacionais viram o lexema",
                "+ - * / = < > <> <= >=", "+", "-", "*", "/", "=", "<", ">", "<>", "<=", ">=");
        checarTerminais("operadores lógicos viram o lexema em minúsculas",
                "and OR Not", "and", "or", "not");
        checarTerminais("símbolos especiais, atribuição e fim",
                "( ) , ; : := .", "(", ")", ",", ";", ":", ":=", ".");
        checarTerminais("constantes string e char",
                "\"texto\" 'a'", "constante_string", "constante_char");

        // Fim de arquivo
        AdaptadorLexico vazio = adaptador("");
        Terminal fim = vazio.proximo();
        checar("texto vazio devolve '$' na linha 1",
                fim.isFimDeArquivo() && fim.getNome().equals("$") && fim.getLinha() == 1);
        checar("depois do fim continua devolvendo '$'", vazio.proximo().isFimDeArquivo());

        AdaptadorLexico ultimaLinha = adaptador("x\n\ny");
        ultimaLinha.proximo();
        ultimaLinha.proximo();
        checar("'$' recebe a linha do último token lido (3)", ultimaLinha.proximo().getLinha() == 3);

        // Erros lexicos: registrados e pulados
        AdaptadorLexico comErros = adaptador("x := 5 @ 2;\nwriteln(\"aberta);\n'ab'");
        List<String> nomes = nomesAteOFim(comErros);
        checar("erros léxicos não chegam ao parser",
                nomes.equals(Arrays.asList("id", ":=", "numero", "numero", ";", "id", "(", "$")));
        List<ErroCompilacao> erros = comErros.getErrosLexicos();
        checar("três erros léxicos registrados ('@', string aberta e o char 'ab')",
                erros.size() == 3);
        checar("primeiro erro: linha 1, caractere inválido",
                erros.get(0).getLinha() == 1 && erros.get(0).getCategoria() == CategoriaErro.LEXICO
                        && erros.get(0).getMensagem().equals("Caractere inválido: @"));
        checar("segundo erro: linha 2, string não fechada",
                erros.get(1).getLinha() == 2
                        && erros.get(1).getMensagem().equals("String não fechada (falta \"): \"aberta);"));
        checar("terceiro erro: linha 3, char com mais de um caractere",
                erros.get(2).getLinha() == 3
                        && erros.get(2).getMensagem().equals("Char deve ter 1 caractere: 'ab'"));
        checar("formato da mensagem completa",
                erros.get(0).toString().equals("Erro léxico na linha 1: Caractere inválido: @"));
        checar("tokens lidos incluem os erros (para a aba de tokens)",
                comErros.getTokensLidos().size() == 10);

        // Caracteres de controle (invisiveis) aparecem na mensagem como uma
        // marca, a mesma que a interface mostra no lugar deles.
        AdaptadorLexico controle = adaptador("x\u0001 y\u007f\n\"a\u0002");
        nomesAteOFim(controle);
        List<String> mensagens = new ArrayList<>();
        for (ErroCompilacao e : controle.getErrosLexicos()) {
            mensagens.add(e.getLinha() + " " + e.getMensagem());
        }
        checar("caractere de controle na mensagem aparece como □ (" + mensagens + ")",
                mensagens.equals(Arrays.asList("1 Caractere inválido: □", "1 Caractere inválido: □",
                        "2 String não fechada (falta \"): \"a□")));
        checar("tabulação e quebra de linha não são caracteres de controle invisíveis; \\u0001 e DEL são",
                !DescricaoErroLexico.invisivel('\t') && DescricaoErroLexico.invisivel('\u0001')
                        && DescricaoErroLexico.invisivel('\u007f') && !DescricaoErroLexico.invisivel('\n')
                        && DescricaoErroLexico.visivel("a\u0000b\tc").equals("a□b\tc"));
        // '\f' e '\v' sao separadores para o lexico: viram espaco, nao a marca.
        checar("'\\f' e '\\v' não são invisíveis e viram espaço (são separadores, como o espaço)",
                !DescricaoErroLexico.invisivel('\f') && !DescricaoErroLexico.invisivel('\u000B')
                        && DescricaoErroLexico.visivel("a\fb\u000Bc").equals("a b c"));

        // Posicoes na sequencia de todos os tokens lidos (ordenam erros lexicos e
        // sintaticos na mesma linha): x=0, @=1, y=2, $=3.
        AdaptadorLexico posicoes = adaptador("x @ y");
        Terminal px = posicoes.proximo();
        Terminal py = posicoes.proximo();
        Terminal pfim = posicoes.proximo();
        checar("posições: x é o token 0, y o 2 (o '@' é o 1), '$' o 3",
                px.getPosicao() == 0 && py.getPosicao() == 2 && pfim.getPosicao() == 3);
        checar("posição do erro léxico '@': 1", posicoes.posicaoDoErroLexico(0) == 1);

        AdaptadorLexico comentario = adaptador("program x;\n/* aberto");
        nomesAteOFim(comentario);
        checar("comentário não fechado vira erro léxico na linha da abertura",
                comentario.getErrosLexicos().size() == 1
                        && comentario.getErrosLexicos().get(0).getLinha() == 2
                        && comentario.getErrosLexicos().get(0).getMensagem()
                                .equals("Comentário não fechado (falta */): /*"));

        // Programa completo, no formato da gramatica
        String programa =
                "program exemplo;\n" +
                "var a, b: integer;\n" +
                "procedure soma(var r: integer; x: integer);\n" +
                "begin r := r + x; end;\n" +
                "begin\n" +
                "  read(a);\n" +
                "  if a >= 10 then b := a mod 3 else b := -a;\n" +
                "  while b <> 0 do soma(a, b);\n" +
                "  writeln(\"fim\", a)\n" +
                "end.";
        List<String> esperado = Arrays.asList(
                "program", "id", ";",
                "var", "id", ",", "id", ":", "tipo", ";",
                "procedure", "id", "(", "var", "id", ":", "tipo", ";", "id", ":", "tipo", ")", ";",
                "begin", "id", ":=", "id", "+", "id", ";", "end", ";",
                "begin",
                "id", "(", "id", ")", ";",
                "if", "id", ">=", "numero", "then", "id", ":=", "id", "mod", "numero",
                "else", "id", ":=", "-", "id", ";",
                "while", "id", "<>", "numero", "do", "id", "(", "id", ",", "id", ")", ";",
                "id", "(", "constante_string", ",", "id", ")",
                "end", ".", "$");
        AdaptadorLexico completo = adaptador(programa);
        List<String> obtido = nomesAteOFim(completo);
        checar("programa completo produz a sequência de terminais esperada", obtido.equals(esperado));
        checar("programa completo não tem erros léxicos", completo.getErrosLexicos().isEmpty());

        System.out.println();
        System.out.println(total + " verificacoes do adaptador, " + falhas + " falha(s).");
        if (falhas > 0) {
            System.exit(1);
        }
    }

    private static AdaptadorLexico adaptador(String fonte) {
        return new AdaptadorLexico(new AnalisadorLexico(fonte, TABELA));
    }

    // Le ate o "$" (inclusive) e devolve os nomes dos terminais.
    private static List<String> nomesAteOFim(AdaptadorLexico adaptador) {
        List<String> nomes = new ArrayList<>();
        Terminal t;
        do {
            t = adaptador.proximo();
            nomes.add(t.getNome());
        } while (!t.isFimDeArquivo());
        return nomes;
    }

    private static void checarTerminais(String descricao, String fonte, String... esperados) {
        List<String> esperado = new ArrayList<>(Arrays.asList(esperados));
        esperado.add("$");
        List<String> obtido = nomesAteOFim(adaptador(fonte));
        boolean ok = obtido.equals(esperado);
        checar(descricao + (ok ? "" : " (obtido: " + obtido + ")"), ok);
    }

    private static void checar(String descricao, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("[OK]    " + Console.ascii(descricao));
        } else {
            falhas++;
            System.out.println("[FALHA] " + Console.ascii(descricao));
        }
    }
}
