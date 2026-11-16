package minipascal.lexico.core;

import minipascal.lexico.model.Token;
import minipascal.lexico.model.TipoToken;
import minipascal.lexico.tabela.TabelaPalavrasReservadas;

/**
 * Le uma cadeia de caracteres e devolve, a cada chamada de proximoToken(),
 * o proximo par lexema/token. Retorna null quando o codigo-fonte acaba.
 *
 * Espacos, tabs, quebras de linha e comentarios de bloco sao consumidos
 * sem gerar token, antes de cada chamada tentar reconhecer algo.
 */
public class AnalisadorLexico {

    private static final int TAMANHO_MAXIMO_IDENTIFICADOR = 63;

    private final String fonte;
    private final TabelaPalavrasReservadas palavrasReservadas;
    private int pos;
    private int linha;
    // Comentario aberto sem "*/" e detectado dentro de pularNaoSignificativos();
    // o flag e a linha de abertura voltam para proximoToken() montar o erro.
    private boolean comentarioNaoFechado;
    private int linhaComentarioNaoFechado;

    public AnalisadorLexico(String fonte, TabelaPalavrasReservadas palavrasReservadas) {
        this.fonte = fonte;
        this.palavrasReservadas = palavrasReservadas;
        this.pos = 0;
        this.linha = 1;
    }

    // Cada chamada: ignora espacos e comentarios, depois escolhe a rotina de
    // leitura pelo primeiro caractere (lookahead de 1) e le o maior lexema
    // possivel a partir dali. Devolve null quando o arquivo termina.
    public Token proximoToken() {
        comentarioNaoFechado = false;
        pularNaoSignificativos();

        // O erro de comentario e entregue no lugar do proximo token. Como a
        // leitura chegou ao fim do arquivo, a chamada seguinte devolve null.
        if (comentarioNaoFechado) {
            return new Token("/*", TipoToken.ERRO_LEXICO, linhaComentarioNaoFechado);
        }

        if (pos >= fonte.length()) {
            return null;
        }

        char c = fonte.charAt(pos);
        // Linha onde o token comeca, guardada antes de a leitura avancar pos.
        int linhaToken = linha;

        // O primeiro caractere decide qual parte do AFD reconhece o token.
        if (ehLetra(c)) {
            return lerIdentificadorOuPalavra(linhaToken);
        }
        if (ehDigito(c)) {
            return lerNumero(linhaToken);
        }
        if (c == '"') {
            return lerString(linhaToken);
        }
        if (c == '\'') {
            return lerChar(linhaToken);
        }

        // Operadores e simbolos: de um caractere, ou de dois (>=, <=, <>, :=).
        switch (c) {
            case '+':
                pos++;
                return new Token("+", TipoToken.OPERADOR_ARITMETICO, linhaToken);
            case '-':
                pos++;
                return new Token("-", TipoToken.OPERADOR_ARITMETICO, linhaToken);
            case '*':
                pos++;
                return new Token("*", TipoToken.OPERADOR_ARITMETICO, linhaToken);
            case '/':
                pos++;
                return new Token("/", TipoToken.OPERADOR_ARITMETICO, linhaToken);
            case '=':
                pos++;
                return new Token("=", TipoToken.OPERADOR_RELACIONAL, linhaToken);
            case '>':
                return lerMaior(linhaToken);
            case '<':
                return lerMenor(linhaToken);
            case '(':
                pos++;
                return new Token("(", TipoToken.SIMBOLO_ESPECIAL, linhaToken);
            case ')':
                pos++;
                return new Token(")", TipoToken.SIMBOLO_ESPECIAL, linhaToken);
            case ',':
                pos++;
                return new Token(",", TipoToken.SIMBOLO_ESPECIAL, linhaToken);
            case ';':
                pos++;
                return new Token(";", TipoToken.SIMBOLO_ESPECIAL, linhaToken);
            case ':':
                return lerDoisPontos(linhaToken);
            case '.':
                pos++;
                return new Token(".", TipoToken.FIM, linhaToken);
            default:
                // Simbolo fora da linguagem: um erro por caractere. Usa code point
                // para que um emoji (dois chars) seja um erro so, nao dois.
                int tamanho = Character.charCount(fonte.codePointAt(pos));
                String invalido = fonte.substring(pos, pos + tamanho);
                pos += tamanho;
                return new Token(invalido, TipoToken.ERRO_LEXICO, linhaToken);
        }
    }

    // Consome espaco/tab/enter e comentarios de bloco. Se cair num '/' que
    // nao e seguido de '*', para aqui e devolve pro switch tratar como
    // divisao - e o mesmo prefixo comum resolvido com 1 caractere de lookahead.
    private void pularNaoSignificativos() {
        while (pos < fonte.length()) {
            char c = fonte.charAt(pos);

            if (ehQuebraDeLinha(c)) {
                avancarContandoLinha();
            } else if (ehEspaco(c)) {
                pos++;
            } else if (c == '/' && caractereSeguinteEh('*')) {
                int linhaAbertura = linha;
                if (!pularComentario()) {
                    comentarioNaoFechado = true;
                    linhaComentarioNaoFechado = linhaAbertura;
                    return;
                }
            } else {
                break;
            }
        }
    }

    // Retorna false se chegar ao fim do arquivo sem encontrar o "*/" de fechamento.
    private boolean pularComentario() {
        pos += 2; // "/*"
        while (pos < fonte.length() && !(fonte.charAt(pos) == '*' && caractereSeguinteEh('/'))) {
            avancarContandoLinha();
        }
        if (pos >= fonte.length()) {
            return false;
        }
        pos += 2; // "*/"
        return true;
    }

    // Avanca um caractere e soma uma linha se ele for uma quebra. O "\r\n"
    // do Windows conta uma vez so (na hora do '\n'); um '\r' sozinho
    // (arquivos de Mac antigo) tambem encerra a linha.
    private void avancarContandoLinha() {
        char c = fonte.charAt(pos);
        if (c == '\n' || (c == '\r' && !caractereSeguinteEh('\n'))) {
            linha++;
        }
        pos++;
    }

    private static boolean ehQuebraDeLinha(char c) {
        return c == '\n' || c == '\r';
    }

    private static boolean ehEspaco(char c) {
        return c == ' ' || c == '\t' || c == '\f' || c == '\u000B';
    }

    // Letras do alfabeto latino, com ou sem acento (o enunciado usa "variavel"
    // como exemplo de identificador). Letras de outros alfabetos sao erro.
    private static boolean ehLetra(char c) {
        return Character.isLetter(c) && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN;
    }

    private static boolean ehDigito(char c) {
        return c >= '0' && c <= '9';
    }

    // Le o identificador inteiro, mas o lexema guarda so os 63 primeiros
    // caracteres; o excedente e consumido para nao virar um segundo token.
    // A mesma busca na tabela decide entre palavra reservada, mod/and/or/not
    // e identificador comum.
    private Token lerIdentificadorOuPalavra(int linhaToken) {
        int inicio = pos;
        while (pos < fonte.length() && (ehLetra(fonte.charAt(pos)) || ehDigito(fonte.charAt(pos)) || fonte.charAt(pos) == '_')) {
            pos++;
        }
        // so os 63 primeiros caracteres ficam no lexema
        int fim = Math.min(pos, inicio + TAMANHO_MAXIMO_IDENTIFICADOR);
        String lexema = fonte.substring(inicio, fim);
        // null: nao e palavra reservada nem operador de palavra, e identificador
        TipoToken tipo = palavrasReservadas.buscar(lexema);
        return new Token(lexema, tipo != null ? tipo : TipoToken.IDENTIFICADOR, linhaToken);
    }

    /*
     * Numeros (estados q2 a q7 e q30 do AFD):
     *   inteiro      12
     *   real         1.5   1.5e-3   2e3   2E+10
     * Se depois do "." nao vier digito, o ponto nao faz parte do numero
     * ("3." e "3" seguido de "."). Um numero colado numa letra ou em "_"
     * ("12abc", "1.5x", "2end", "2e" sem digitos no expoente) vira um unico
     * erro lexico, "numero mal formado", com todas as letras e digitos colados.
     */
    private Token lerNumero(int linhaToken) {
        int inicio = pos;
        pularDigitos();
        boolean real = false;
        boolean malFormado = false;

        // Parte fracionaria: so se o ponto for seguido de digito.
        if (proximoCaractereEh('.') && proximoCaractereEhDigito()) {
            pos++;
            pularDigitos();
            real = true;
        }

        // Expoente: e/E, sinal opcional e pelo menos um digito. Um "e" sem
        // digitos depois ja e numero mal formado (estado q5 do AFD).
        if (proximoCaractereEh('e') || proximoCaractereEh('E')) {
            pos++;
            boolean temSinal = proximoCaractereEh('+') || proximoCaractereEh('-');
            int depoisDoSinal = temSinal ? pos + 1 : pos;
            if (depoisDoSinal < fonte.length() && ehDigito(fonte.charAt(depoisDoSinal))) {
                pos = depoisDoSinal;
                pularDigitos();
                real = true;
            } else {
                malFormado = true;
            }
        }

        // Letra ou "_" colado no numero: consome tudo o que estiver colado (q30).
        if (pos < fonte.length() && (ehLetra(fonte.charAt(pos)) || fonte.charAt(pos) == '_')) {
            malFormado = true;
            while (pos < fonte.length()
                    && (ehLetra(fonte.charAt(pos)) || ehDigito(fonte.charAt(pos)) || fonte.charAt(pos) == '_')) {
                pos++;
            }
        }

        String lexema = fonte.substring(inicio, pos);
        if (malFormado) {
            return new Token(lexema, TipoToken.ERRO_LEXICO, linhaToken);
        }
        return new Token(lexema, real ? TipoToken.NUMERO_REAL : TipoToken.NUMERO_INTEIRO, linhaToken);
    }

    private void pularDigitos() {
        while (pos < fonte.length() && ehDigito(fonte.charAt(pos))) {
            pos++;
        }
    }

    // Strings da linguagem sao sempre de uma linha so (nenhum exemplo do
    // enunciado mostra string multi-linha). Por isso, se nao fechar antes
    // de uma quebra de linha, paramos ali - sem consumir o '\r' ou '\n' - e
    // devolvemos o controle pro scanner normal, que trata a quebra de
    // linha como nao-significativa e segue tokenizando o resto do arquivo.
    // Isso evita que uma aspa esquecida engula o programa inteiro ate o EOF.
    private Token lerString(int linhaToken) {
        int inicio = pos;
        pos++;
        while (pos < fonte.length() && fonte.charAt(pos) != '"' && !ehQuebraDeLinha(fonte.charAt(pos))) {
            pos++;
        }
        // parou na aspa de fechamento ou numa quebra de linha / fim do texto?
        boolean fechada = pos < fonte.length() && fonte.charAt(pos) == '"';
        if (fechada) {
            pos++;
        }
        String lexema = fonte.substring(inicio, pos);
        return new Token(lexema, fechada ? TipoToken.CONSTANTE_STRING : TipoToken.ERRO_LEXICO, linhaToken);
    }

    /*
     * Constante char (estados q27, q28, q29, q33, q36 e q38 do AFD): exatamente
     * um caractere entre aspas simples, como 'x'. Erros:
     *   ''  e  'ab'   aspas fechadas, mas sem 1 caractere: um erro so, ate a
     *                 aspa que fecha;
     *   'a  e  'ab    sem a aspa que fecha: o erro vai ate o fim da linha
     *                 (como na string), sem consumir a quebra de linha.
     */
    private Token lerChar(int linhaToken) {
        int inicio = pos;
        pos++;
        int caracteres = 0;
        while (pos < fonte.length() && fonte.charAt(pos) != '\'' && !ehQuebraDeLinha(fonte.charAt(pos))) {
            pos += Character.charCount(fonte.codePointAt(pos));
            caracteres++;
        }
        boolean fechado = pos < fonte.length() && fonte.charAt(pos) == '\'';
        if (fechado) {
            pos++;
        }
        String lexema = fonte.substring(inicio, pos);
        boolean valido = fechado && caracteres == 1;
        return new Token(lexema, valido ? TipoToken.CONSTANTE_CHAR : TipoToken.ERRO_LEXICO, linhaToken);
    }

    // lerMaior, lerMenor e lerDoisPontos leem o operador mais longo possivel
    // (>=, <=, <>, :=). O caractere ja foi confirmado, entao pos++ vem primeiro.
    private Token lerMaior(int linhaToken) {
        pos++;
        if (proximoCaractereEh('=')) {
            pos++;
            return new Token(">=", TipoToken.OPERADOR_RELACIONAL, linhaToken);
        }
        return new Token(">", TipoToken.OPERADOR_RELACIONAL, linhaToken);
    }

    private Token lerMenor(int linhaToken) {
        pos++;
        if (proximoCaractereEh('=')) {
            pos++;
            return new Token("<=", TipoToken.OPERADOR_RELACIONAL, linhaToken);
        }
        if (proximoCaractereEh('>')) {
            pos++;
            return new Token("<>", TipoToken.OPERADOR_RELACIONAL, linhaToken);
        }
        return new Token("<", TipoToken.OPERADOR_RELACIONAL, linhaToken);
    }

    private Token lerDoisPontos(int linhaToken) {
        pos++;
        if (proximoCaractereEh('=')) {
            pos++;
            return new Token(":=", TipoToken.ATRIBUICAO, linhaToken);
        }
        return new Token(":", TipoToken.SIMBOLO_ESPECIAL, linhaToken);
    }

    // Compara o caractere em pos.
    private boolean proximoCaractereEh(char esperado) {
        return pos < fonte.length() && fonte.charAt(pos) == esperado;
    }

    // Compara o caractere em pos + 1, sem consumir nada.
    private boolean caractereSeguinteEh(char esperado) {
        return pos + 1 < fonte.length() && fonte.charAt(pos + 1) == esperado;
    }

    // Usado em lerNumero, onde pos esta no ponto: o digito esperado fica em pos + 1.
    private boolean proximoCaractereEhDigito() {
        return pos + 1 < fonte.length() && ehDigito(fonte.charAt(pos + 1));
    }
}