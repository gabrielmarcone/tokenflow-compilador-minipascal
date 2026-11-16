# Arquivos de teste da Unidade II

Programas em Mini_Pascal para testar a análise completa (léxica, sintática e semântica).
Para cada programa, `esperado/` guarda o resultado esperado: `OK` ou os erros (tipo, linha e
mensagem) e a tabela de símbolos. É exatamente o que a linha de comando grava:

```bash
java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica --tabela testes-integracao/unidade2/01_programa_completo.txt saida.txt
```

Na interface, basta abrir o arquivo (Ctrl+O) e clicar em Analisar (F5).

## Os testes

| Arquivo | O que testa | Resultado |
|---|---|---|
| `01_programa_completo.txt` | Programa correto que usa toda a linguagem: os quatro tipos, vários blocos `var`, procedimentos sem parâmetros, com parâmetros por valor e `var`, recursão, `if` com e sem `else` (inclusive o `else` pendente), `while`, blocos `begin`/`end`, todos os operadores, sinal, notação científica, comparação de comparações, as conversões permitidas (integer em real, char em string), `read`/`write`/`writeln` e maiúsculas/minúsculas | `OK` |
| `02_erros_lexicos.txt` | Caracteres fora da linguagem (`#`, `$`, `!` de `!=`, `{`, `}`, `?`, `π`), número colado em letras (`12abc`), char com dois caracteres (`'ab'`) e comentário nunca fechado. Sem eles o programa fica correto, então só aparecem erros léxicos e a análise semântica vai até o fim. Também: identificador com acento, identificador com mais de 63 caracteres (truncado, sem erro), número real com expoente sem ponto (`2E-3`) e caracteres especiais dentro de string e comentário (sem erro) | 10 erros léxicos |
| `03_erros_sintaticos.txt` | Vírgula faltando na declaração, `)` faltando, `then` e `do` faltando, `=` no lugar de `:=`, `;` antes do `else`, operador sem operando, `);` faltando e `.` final faltando. Cada erro diz o que era esperado, e a recuperação permite encontrar todos numa execução só | 9 erros sintáticos |
| `04_erros_semanticos_nomes.txt` | Variável, parâmetro e procedimento declarados duas vezes no mesmo escopo; nome não declarado (relatado uma vez por escopo); variável local usada fora do procedimento; procedimento chamado antes de ser declarado; procedimento usado como variável e o contrário; atribuição ao nome do programa e a `read` | 16 erros semânticos |
| `05_erros_semanticos_tipos.txt` | Atribuições incompatíveis (real em integer, string em char, comparação em integer...), `div` e `mod` com real, operadores com texto, sinal em texto, comparações entre tipos incompatíveis, condição de `if` e `while` que não é comparação, erro numa expressão de duas linhas (vale a linha do operador) e expressão com erro que não gera outras mensagens | 22 erros semânticos |
| `06_erros_semanticos_chamadas.txt` | Número de argumentos (a mais e a menos), tipo de cada argumento, parâmetro `var` recebendo constante, expressão, expressão entre parênteses e variável de outro tipo; `read` sem argumentos ou com expressão; `write` sem argumentos; `writeln` sem argumentos (válido) | 19 erros semânticos |
| `07_escopos.txt` | Programa correto com procedimento dentro de procedimento (níveis 1, 2 e 3), parâmetro que esconde a variável de fora, uso de nomes de fora, recursão, nome do programa e `write` redeclarados, maiúsculas/minúsculas. A tabela de símbolos mostra cada escopo | `OK` |
| `08_erros_misturados.txt` | Erros das três fases no mesmo programa: ordem por linha e, na mesma linha, pela posição; erro léxico que causa erro sintático (`5 @ 2`); string sem fechar; a análise semântica para no primeiro erro sintático (o erro de tipo da linha 18 não aparece), e a tabela de símbolos avisa isso | 4 léxicos, 3 sintáticos, 3 semânticos |
| `apresentacao.txt` | Programa da apresentação, com um erro de cada fase (ver o roteiro abaixo) | 1 léxico, 1 sintático, 2 semânticos |
| `apresentacao_corrigido.txt` | O mesmo programa com os quatro erros corrigidos | `OK` |

## Como os resultados esperados foram conferidos

Os resultados esperados não foram copiados da saída do programa sem olhar. Para cada arquivo,
o resultado foi escrito à mão a partir do enunciado e das regras da linguagem, antes de rodar
o compilador: cada erro (tipo, linha e mensagem; nos sintáticos, o símbolo encontrado e o
conjunto do que era esperado) e cada linha da tabela de símbolos (escopo, nível, nome,
categoria, tipo, linha da declaração e parâmetros). Depois, a saída do compilador foi
comparada campo por campo com o que foi escrito à mão. As duas só diferiram no texto da
mensagem de fim de arquivo (`Fim do arquivo inesperado; esperado '.'`), que estava certo.

A suíte `VerificacaoTestesUnidade2`, que roda em `.\build.bat teste`, passa cada arquivo pela
linha de comando e confere:

- que a saída gravada é igual à de `esperado/`;
- que o código de saída é 0 para `OK` e 1 quando há erros;
- que o console mostra o mesmo texto, sem acentos;
- que o resultado não muda com quebras de linha do Windows (CRLF).

Ela também confere se todo programa tem um resultado esperado e se há pelo menos 5 testes,
como pede o enunciado.

Para rodar só essa suíte, a partir da raiz do projeto:

```bash
java -cp TokenFlow.jar minipascal.verificacao.VerificacaoTestesUnidade2
```

Para um teste novo, crie o programa nesta pasta e grave o resultado esperado em `esperado/`
com o mesmo nome, depois de conferi-lo.

## Roteiro da apresentação

`apresentacao.txt` calcula a média de duas notas de um aluno e tem quatro erros, um de cada
fase e dois semânticos:

| Linha | Fase | O que está errado |
|---|---|---|
| 16 | Semântico | `faltas` (integer) passada para o parâmetro `var resultado` (real): num parâmetro `var` os tipos devem ser iguais |
| 18 | Semântico | `situacao` não foi declarada |
| 21 | Léxico | `!` não existe na linguagem (`!=` é do C; em Pascal, diferente é `<>`). Ele some da entrada e a análise continua |
| 23 | Sintático | falta o `then`; a mensagem mostra o que era esperado ali |

1. Abra `apresentacao.txt` (Ctrl+O) e mostre o programa: variáveis, o procedimento
   `calcular` com parâmetro `var` e o corpo.
2. Clique em **Analisar** (F5). A faixa mostra "4 erros encontrados: 1 léxico, 1 sintático e
   2 semânticos" e avisa que a análise semântica parou no primeiro erro sintático. As linhas
   com erro ficam marcadas com `●` no código.
3. Na aba **Erros**, clique em cada erro: a linha fica selecionada no código. A mensagem
   inteira aparece ao parar o mouse sobre ela.
4. Na aba **Tokens**, mostre o `!` classificado como erro léxico (a saída da Unidade I).
5. Na aba **Pilha**, clique em **Próximo erro**: é o passo em que o analisador acabou de
   ler o `18` de `if faltas > 18` (estado 39) e encontrou `writeln`. O detalhe embaixo mostra
   a pilha, os símbolos, o que falta ler e a ação. Clique em **Ver na tabela SLR**: a célula
   ACTION[39, id] está vazia, e é por isso que é um erro. De volta à Pilha, o passo seguinte
   é a recuperação em modo pânico: desempilha 5 estados, considera a lista de comandos
   reconhecida (o `if` incompleto fica para trás) e continua a análise no `writeln`.
6. Corrija os erros no próprio editor; a faixa fica amarela avisando que o código mudou:
   - linha 7: `nome, situacao: string;`
   - linha 16: `calcular(nota1, nota2, media);`
   - linha 21: `<>` no lugar de `!=`
   - linha 23: `then` antes do `writeln`
   
   O resultado é igual a `apresentacao_corrigido.txt`, que pode ser aberto se não der tempo.
7. Analise de novo (F5): a faixa fica verde com "OK: o programa está correto".
8. Na aba **Símbolos**, mostre os escopos: `externo` (nível 0: predefinidos e o nome do
   programa), `global` (nível 1) e `calcular` (nível 2), com os parâmetros e o `var`.
9. Na aba **Pilha**, vá ao último passo (aceita) e, em **Gramática e SLR(1)**, mostre a
   gramática numerada (os `rN` da pilha), FIRST e FOLLOW e os estados LR(0).
10. Se quiser mostrar a linha de comando, que é a saída pedida no enunciado:

    ```bash
    java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica testes-integracao/unidade2/apresentacao.txt
    java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica testes-integracao/unidade2/apresentacao_corrigido.txt
    ```
