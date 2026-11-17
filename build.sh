#!/bin/sh
# Compila o projeto e gera o TokenFlow.jar.
#   ./build.sh          compila e gera o jar
#   ./build.sh teste    compila, gera o jar e roda todas as verificacoes
# Opcional: JAVAC_OPTS para flags extras do javac (ex.: --module-path do JavaFX
# quando o JDK nao traz JavaFX embutido).
set -e
cd "$(dirname "$0")"

JAVAC=javac
JAR=jar
JAVA=java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
    JAVA="$JAVA_HOME/bin/java"
fi

rm -rf out
mkdir -p out
find src -name '*.java' > out/fontes.txt
"$JAVAC" $JAVAC_OPTS -encoding UTF-8 -d out @out/fontes.txt

cp src/minipascal/gui/*.fxml src/minipascal/gui/*.css src/minipascal/gui/*.png out/minipascal/gui/
cp src/minipascal/sintatico/gramatica/*.txt out/minipascal/sintatico/gramatica/

printf 'Main-Class: minipascal.gui.Iniciar\n' > out/manifest.txt
"$JAR" cfm TokenFlow.jar out/manifest.txt -C out minipascal

echo "TokenFlow.jar gerado."

if [ "$1" = "teste" ]; then
    for suite in lexico.verificacao.VerificacaoFase3 lexico.verificacao.VerificacaoFase4 \
                 lexico.verificacao.VerificacaoFase5 lexico.verificacao.VerificacaoFase6 \
                 lexico.verificacao.VerificacaoAbsurda lexico.verificacao.VerificacaoRobustez \
                 lexico.verificacao.VerificacaoAfd sintatico.verificacao.VerificacaoAdaptador \
                 sintatico.verificacao.VerificacaoGeradorSLR sintatico.verificacao.VerificacaoParserExpressoes \
                 sintatico.verificacao.VerificacaoParserCompleto sintatico.verificacao.VerificacaoRecuperacao \
                 sintatico.verificacao.VerificacaoRastro \
                 semantico.verificacao.VerificacaoTabelaSimbolos semantico.verificacao.VerificacaoSemantica \
                 verificacao.VerificacaoArquitetura verificacao.VerificacaoTestesUnidade2; do
        "$JAVA" -cp out "minipascal.$suite" > out/resultado.txt || { grep FALHA out/resultado.txt; echo "$suite falhou."; exit 1; }
        grep "falha(s)" out/resultado.txt
    done
    echo "Todas as verificacoes passaram."
fi