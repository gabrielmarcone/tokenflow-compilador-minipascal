package minipascal.sintatico.gramatica;

import minipascal.lexico.io.LeitorArquivoFonte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lê uma gramática escrita em texto, uma regra por linha:
 *
 *   # comentário (linha inteira)
 *   EXPR -> SIEXPR | SIEXPR OPREL SIEXPR
 *   FATOR -> numero | id | ( EXPR )
 *        | constante_string          (linha começando com | continua a regra anterior)
 *   LISTA -> ε                        (produção vazia; "eps" também é aceito)
 *
 * Os símbolos são separados por espaços, inclusive em volta de "->" e "|".
 * Cada alternativa vira uma produção, numerada a partir de 1 na ordem do
 * arquivo. O símbolo "|" não pode ser terminal.
 */
public final class LeitorGramatica {

    private static final String SETA = "->";
    private static final String ALTERNATIVA = "|";
    private static final Set<String> VAZIO = new HashSet<>(Arrays.asList("ε", "eps"));

    private LeitorGramatica() {
    }

    // Lê um arquivo do disco (UTF-8, com recuo para ANSI, como os fontes Mini_Pascal).
    public static Gramatica lerArquivo(String caminho) throws IOException {
        return lerTexto(LeitorArquivoFonte.ler(caminho));
    }

    public static boolean existeRecurso(String nome) {
        return LeitorGramatica.class.getResource(nome) != null;
    }

    // Lê uma gramática que acompanha o programa (pasta minipascal/sintatico/gramatica do jar).
    public static Gramatica lerRecurso(String nome) {
        try (InputStream in = LeitorGramatica.class.getResourceAsStream(nome)) {
            if (in == null) {
                throw new GramaticaInvalidaException("arquivo de gramática '" + nome + "' não encontrado.");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, lidos);
            }
            String texto = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (texto.startsWith("﻿")) {
                texto = texto.substring(1);
            }
            return lerTexto(texto);
        } catch (IOException e) {
            throw new GramaticaInvalidaException("não foi possível ler '" + nome + "': " + e.getMessage());
        }
    }

    public static Gramatica lerTexto(String texto) {
        List<Producao> producoes = new ArrayList<>();
        // Primeira linha em que cada símbolo em MAIÚSCULAS aparece num corpo,
        // para apontar a linha quando ele não tiver produção.
        Map<String, Integer> primeiroUso = new LinkedHashMap<>();
        Set<String> cabecas = new HashSet<>();
        String cabecaAtual = null;

        String[] linhas = texto.split("\r\n|\r|\n", -1);
        for (int i = 0; i < linhas.length; i++) {
            int numeroLinha = i + 1;
            String linha = linhas[i].trim();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }
            List<String> simbolos = new ArrayList<>(Arrays.asList(linha.split("\\s+")));

            String cabeca;
            if (simbolos.get(0).equals(ALTERNATIVA)) {
                if (cabecaAtual == null) {
                    throw new GramaticaInvalidaException(numeroLinha,
                            "a linha começa com '|', mas não há regra anterior para continuar.");
                }
                cabeca = cabecaAtual;
                // tira o "|" inicial; o resto da linha são alternativas da mesma cabeça
                simbolos.remove(0);
            } else {
                if (simbolos.size() < 2 || !simbolos.get(1).equals(SETA)) {
                    throw new GramaticaInvalidaException(numeroLinha,
                            "esperado 'CABECA -> corpo' (com espaços em volta de '->').");
                }
                cabeca = simbolos.get(0);
                if (!Gramatica.ehNomeDeNaoTerminal(cabeca)) {
                    throw new GramaticaInvalidaException(numeroLinha, "a cabeça '" + cabeca
                            + "' não é um nome de não terminal (use MAIÚSCULAS, dígitos e _).");
                }
                simbolos = simbolos.subList(2, simbolos.size());
                cabecaAtual = cabeca;
            }
            cabecas.add(cabeca);

            for (List<String> alternativa : separarAlternativas(simbolos)) {
                producoes.add(new Producao(producoes.size() + 1, cabeca,
                        corpoDaAlternativa(alternativa, numeroLinha, primeiroUso)));
            }
        }

        for (Map.Entry<String, Integer> uso : primeiroUso.entrySet()) {
            if (!cabecas.contains(uso.getKey())) {
                throw new GramaticaInvalidaException(uso.getValue(), "o não terminal '" + uso.getKey()
                        + "' é usado mas não tem nenhuma produção.");
            }
        }
        return new Gramatica(producoes);
    }

    private static List<List<String>> separarAlternativas(List<String> simbolos) {
        List<List<String>> alternativas = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        for (String s : simbolos) {
            if (s.equals(ALTERNATIVA)) {
                alternativas.add(atual);
                atual = new ArrayList<>();
            } else {
                atual.add(s);
            }
        }
        alternativas.add(atual);
        return alternativas;
    }

    private static List<String> corpoDaAlternativa(List<String> alternativa, int numeroLinha,
                                                   Map<String, Integer> primeiroUso) {
        if (alternativa.isEmpty()) {
            throw new GramaticaInvalidaException(numeroLinha,
                    "alternativa vazia; para a produção vazia escreva ε (ou eps).");
        }
        boolean temVazio = false;
        for (String s : alternativa) {
            if (VAZIO.contains(s)) {
                temVazio = true;
            }
        }
        if (temVazio) {
            if (alternativa.size() > 1) {
                throw new GramaticaInvalidaException(numeroLinha,
                        "ε (ou eps) deve aparecer sozinho na alternativa.");
            }
            return new ArrayList<>();
        }
        for (String s : alternativa) {
            if (s.equals(SETA)) {
                throw new GramaticaInvalidaException(numeroLinha, "'->' repetido na mesma regra.");
            }
            if (Gramatica.ehNomeDeNaoTerminal(s) && !primeiroUso.containsKey(s)) {
                primeiroUso.put(s, numeroLinha);
            }
        }
        return alternativa;
    }
}
