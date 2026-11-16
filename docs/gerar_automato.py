"""
Desenha docs/automato_completo.jpg, a figura do AFD do analisador lexico.

O AFD desenhado aqui e o mesmo da tabela de transicoes de
src/minipascal/lexico/verificacao/VerificacaoAfd.java, que confere o
analisador contra ele. Se o automato mudar, mude os dois.

Uso (precisa do rsvg-convert e do ImageMagick):
    python3 docs/gerar_automato.py
"""
import math
import os
import subprocess
import tempfile

R = 17          # raio do estado
R_INTERNO = 13  # segundo circulo dos estados finais
TRONCO_X = 217  # linha vertical que sai de q0
LARGURA, ALTURA = 1100, 1730

partes = []
estados = {}


def texto(x, y, conteudo, tamanho=10, ancora="middle", negrito=False):
    peso = ' font-weight="bold"' if negrito else ""
    conteudo = conteudo.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    partes.append(f'<text x="{x}" y="{y}" font-size="{tamanho}" text-anchor="{ancora}"{peso}>{conteudo}</text>')


def rotulo_de_aresta(x, y, conteudo):
    # Simbolos sozinhos ('.', '=', aspas) saem maiores e em negrito, para serem vistos.
    if len(conteudo) <= 2 and not conteudo.isalnum():
        texto(x, y, conteudo, 13, negrito=True)
    else:
        texto(x, y, conteudo)


def estado(nome, x, y, final=False):
    estados[nome] = (x, y)
    partes.append(f'<circle cx="{x}" cy="{y}" r="{R}" fill="white" stroke="black" stroke-width="1.3"/>')
    if final:
        partes.append(f'<circle cx="{x}" cy="{y}" r="{R_INTERNO}" fill="none" stroke="black" stroke-width="1.3"/>')
    texto(x, y + 4, nome, 11, negrito=True)


def ponta(x, y, angulo):
    # Seta triangular apontando na direcao do angulo (radianos).
    a1, a2 = angulo + math.radians(155), angulo - math.radians(155)
    p1 = (x + 9 * math.cos(a1), y + 9 * math.sin(a1))
    p2 = (x + 9 * math.cos(a2), y + 9 * math.sin(a2))
    partes.append(f'<polygon points="{x},{y} {p1[0]:.1f},{p1[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}" fill="black"/>')


def borda(origem, destino):
    # Pontos nas bordas dos dois circulos, na linha que liga os centros.
    (x1, y1), (x2, y2) = origem, destino
    ang = math.atan2(y2 - y1, x2 - x1)
    return (x1 + R * math.cos(ang), y1 + R * math.sin(ang)), (x2 - R * math.cos(ang), y2 - R * math.sin(ang)), ang


def reta(a, b, rotulo="", dx=0, dy=-7, tracejada=False):
    (x1, y1), (x2, y2), ang = borda(estados[a], estados[b])
    traco = ' stroke-dasharray="6,4"' if tracejada else ""
    partes.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="black" stroke-width="1.2"{traco}/>')
    ponta(x2, y2, ang)
    if rotulo:
        rotulo_de_aresta((x1 + x2) / 2 + dx, (y1 + y2) / 2 + dy, rotulo)


def curva(p1, controle, p2, rotulo="", rx=None, ry=None, tracejada=False):
    # Bezier quadratica de p1 a p2; a seta segue a tangente no fim.
    traco = ' stroke-dasharray="6,4"' if tracejada else ""
    partes.append(f'<path d="M {p1[0]:.1f},{p1[1]:.1f} Q {controle[0]:.1f},{controle[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}"'
                  f' fill="none" stroke="black" stroke-width="1.2"{traco}/>')
    ponta(p2[0], p2[1], math.atan2(p2[1] - controle[1], p2[0] - controle[0]))
    if rotulo:
        rotulo_de_aresta(rx, ry, rotulo)


def no_circulo(nome, graus):
    x, y = estados[nome]
    return (x + R * math.cos(math.radians(graus)), y + R * math.sin(math.radians(graus)))


def laco(nome, rotulo, lado="cima"):
    # Transicao do estado para ele mesmo.
    x, y = estados[nome]
    if lado == "cima":
        p1, p2 = no_circulo(nome, -120), no_circulo(nome, -60)
        partes.append(f'<path d="M {p1[0]:.1f},{p1[1]:.1f} C {x - 22},{y - 50} {x + 22},{y - 50} {p2[0]:.1f},{p2[1]:.1f}"'
                      f' fill="none" stroke="black" stroke-width="1.2"/>')
        ponta(p2[0], p2[1], math.radians(100))
        texto(x, y - 43, rotulo)
    else:
        p1, p2 = no_circulo(nome, 120), no_circulo(nome, 60)
        partes.append(f'<path d="M {p1[0]:.1f},{p1[1]:.1f} C {x - 22},{y + 50} {x + 22},{y + 50} {p2[0]:.1f},{p2[1]:.1f}"'
                      f' fill="none" stroke="black" stroke-width="1.2"/>')
        ponta(p2[0], p2[1], math.radians(-100))
        texto(x, y + 52, rotulo)


def ramo(y, rotulo, destino):
    # Galho do tronco de q0 ate o primeiro estado de cada classe.
    x = estados[destino][0] - R
    partes.append(f'<line x1="{TRONCO_X}" y1="{y}" x2="{x}" y2="{y}" stroke="black" stroke-width="1.2"/>')
    ponta(x, y, 0)
    texto(283, y - 7, rotulo, 12, negrito=True)


def linha(pontos, tracejada=False, seta=True):
    traco = ' stroke-dasharray="6,4"' if tracejada else ""
    d = " ".join(f"{x},{y}" for x, y in pontos)
    partes.append(f'<polyline points="{d}" fill="none" stroke="black" stroke-width="1.2"{traco}/>')
    if seta:
        (x1, y1), (x2, y2) = pontos[-2], pontos[-1]
        ponta(x2, y2, math.atan2(y2 - y1, x2 - x1))


# ---------------------------------------------------------------- q0 e tronco
estado("q0", 108, 60)
linha([(58, 60), (108 - R, 60)])
p1, p2 = no_circulo("q0", 120), no_circulo("q0", 60)
partes.append(f'<path d="M {p1[0]:.1f},{p1[1]:.1f} C 80,110 136,110 {p2[0]:.1f},{p2[1]:.1f}" fill="none" stroke="black" stroke-width="1.2"/>')
ponta(p2[0], p2[1], math.radians(-100))
texto(108, 106, "espaço, tab,")
texto(108, 118, "\\f, \\v, \\n, \\r")
texto(890, 54, "AFD do analisador léxico", 14, negrito=True)

# ------------------------------------------------------- comentario  /* */
estado("q11", 367, 162, True)
estado("q31", 491, 162)
estado("q32", 615, 162)
estado("q37", 553, 245, True)
reta("q11", "q31", "*", dy=-9)
reta("q31", "q32", "*", dy=-9)
laco("q31", "qualquer outro")
laco("q32", "*")
curva(no_circulo("q32", -100), (553, 70), no_circulo("q31", -80), "qualquer outro", 553, 98)
reta("q31", "q37", "fim do arquivo", dx=-46, dy=0)
reta("q32", "q37", "fim do arquivo", dx=48, dy=0)
linha([(615 + R, 162), (723, 162), (723, 26), (108, 26), (108, 60 - R)])
texto(676, 155, "/", 12, negrito=True)

# ---------------------------------------------------------- identificador
estado("q1", 367, 323, True)
laco("q1", "letra latina, dígito 0-9, _")

# ---------------------------------------------------------------- numeros
Y = 430
estado("q2", 367, Y, True)
estado("q3", 491, Y)
estado("q4", 615, Y, True)
estado("q5", 740, Y, True)
estado("q6", 864, Y)
estado("q7", 988, Y, True)
estado("q30", 677, Y + 130, True)
laco("q2", "dígito 0-9")
laco("q4", "dígito 0-9")
laco("q7", "dígito 0-9")
reta("q2", "q3", ".", dy=-9)
reta("q3", "q4", "dígito 0-9", dy=-8)
reta("q4", "q5", "e, E", dy=-8)
reta("q5", "q6", "+, -", dy=-8)
reta("q6", "q7", "dígito 0-9", dy=-8)
curva(no_circulo("q2", -40), (553, 300), no_circulo("q5", -140), "e, E", 553, 358)
curva(no_circulo("q5", -40), (864, 362), no_circulo("q7", -140), "dígito 0-9", 864, 386)
curva(no_circulo("q3", 130), (429, 478), no_circulo("q2", 50), "retrocesso", 429, 478, tracejada=True)
curva(no_circulo("q6", 130), (802, 478), no_circulo("q5", 50), "retrocesso", 802, 478, tracejada=True)
# letra ou "_" colado no numero: as quatro saidas se juntam numa linha so ate q30
BARRA = Y + 75
for origem in ("q2", "q4", "q5", "q7"):
    x = estados[origem][0]
    linha([(x, Y + R), (x, BARRA)], seta=False)
linha([(367, BARRA), (988, BARRA)], seta=False)
linha([(677, BARRA), (677, Y + 130 - R)])
texto(683, BARRA + 22, "letra latina, _", ancora="start")
p1, p2 = no_circulo("q30", -30), no_circulo("q30", 30)
partes.append(f'<path d="M {p1[0]:.1f},{p1[1]:.1f} C {677 + 55},{Y + 105} {677 + 55},{Y + 155} {p2[0]:.1f},{p2[1]:.1f}"'
              f' fill="none" stroke="black" stroke-width="1.2"/>')
ponta(p2[0], p2[1], math.radians(200))
texto(738, Y + 134, "letra latina, dígito 0-9, _", ancora="start")

# -------------------------------------------------------------- operadores
OPS = 650
for i, (rotulo, nome) in enumerate([("+", "q8"), ("-", "q9"), ("*", "q10"), ("=", "q12"), (">", "q13")]):
    estado(nome, 367, OPS + 49 * i, True)
estado("q14", 491, OPS + 49 * 4, True)
reta("q13", "q14", "=", dy=-9)
estado("q15", 367, 926, True)
estado("q16", 491, 893, True)
estado("q17", 491, 958, True)
reta("q15", "q16", "=", dy=-9)
reta("q15", "q17", ">", dy=14)
for i, (rotulo, nome) in enumerate([("(", "q18"), (")", "q19"), (",", "q20"), (";", "q21"), (":", "q22"), (".", "q24")]):
    estado(nome, 367, 1012 + 49 * i, True)
estado("q23", 491, 1012 + 49 * 4, True)
reta("q22", "q23", "=", dy=-9)

# ------------------------------------------------------------------ string
S = 1336
estado("q25", 367, S)
estado("q26", 491, S, True)
estado("q35", 367, S + 75, True)
laco("q25", "qualquer outro")
reta("q25", "q26", '"', dy=-9)
reta("q25", "q35", "\\n, \\r, fim do arquivo *", dx=12, dy=4)
partes[-1] = partes[-1].replace('text-anchor="middle"', 'text-anchor="start"')

# -------------------------------------------------------------------- char
C = 1500
estado("q27", 367, C)
estado("q28", 491, C)
estado("q29", 615, C, True)
estado("q38", 740, C, True)
estado("q33", 553, C + 95)
estado("q36", 367, C + 95, True)
reta("q27", "q28", "qualquer outro", dy=-8)
reta("q28", "q29", "'", dy=-9)
curva(no_circulo("q27", -40), (553, 1418), no_circulo("q38", -140), "'", 553, 1450)
reta("q28", "q33", "qualquer outro", dx=46, dy=-4)
reta("q33", "q38", "'", dx=10, dy=-8)
laco("q33", "qualquer outro", lado="baixo")
reta("q27", "q36", "\\n, \\r, fim *", dx=-12, dy=4)
partes[-1] = partes[-1].replace('text-anchor="middle"', 'text-anchor="end"')
reta("q28", "q36", "\\n, \\r, fim *", dx=44, dy=20)
reta("q33", "q36", "\\n, \\r, fim *", dy=16)

# ----------------------------------------------------------- qualquer outro
estado("q34", 367, 1690, True)

# -------------------------------------------------------- tronco e galhos
galhos = [(162, "/", "q11"), (323, "letra latina", "q1"), (Y, "dígito 0-9", "q2"),
          (OPS, "+", "q8"), (OPS + 49, "-", "q9"), (OPS + 98, "*", "q10"), (OPS + 147, "=", "q12"),
          (OPS + 196, ">", "q13"), (926, "<", "q15"), (1012, "(", "q18"), (1061, ")", "q19"),
          (1110, ",", "q20"), (1159, ";", "q21"), (1208, ":", "q22"), (1257, ".", "q24"),
          (S, '"', "q25"), (C, "'", "q27"), (1690, "qualquer outro", "q34")]
linha([(108 + R, 60), (TRONCO_X, 60), (TRONCO_X, 1690)], seta=False)
for y, rotulo, destino in galhos:
    ramo(y, rotulo, destino)
# os rotulos de letras saem menores, como na figura original
for i, p in enumerate(partes):
    for palavra in ("letra latina", "dígito 0-9", "qualquer outro"):
        if f'font-size="12" text-anchor="middle" font-weight="bold">{palavra}<' in p:
            partes[i] = p.replace('font-size="12"', 'font-size="10"').replace(' font-weight="bold"', "")

# ---------------------------------------------------------------- legenda
L = 720
estado("_n", 768, L)
partes[-1] = partes[-1].replace(">_n<", "><")
estado("_f", 768, L + 40, True)
partes[-1] = partes[-1].replace(">_f<", "><")
texto(792, L + 4, "estado não final", ancora="start")
texto(792, L + 44, "estado final (token ou erro léxico)", ancora="start")
partes.append(f'<line x1="752" y1="{L + 80}" x2="780" y2="{L + 80}" stroke="black" stroke-width="1.2" stroke-dasharray="6,4"/>')
ponta(780, L + 80, 0)
texto(792, L + 84, "retrocesso ao último estado final visitado", ancora="start")
partes.append(f'<line x1="752" y1="{L + 113}" x2="780" y2="{L + 113}" stroke="black" stroke-width="1.2"/>')
ponta(780, L + 113, 0)
texto(792, L + 117, "transição (símbolo lido sobre a seta)", ancora="start")
texto(768, L + 150, "*", 12, negrito=True)
texto(792, L + 150, "não consome o caractere (ele começa o próximo token)", ancora="start")

svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{LARGURA}" height="{ALTURA}" '
       f'font-family="DejaVu Sans, Arial, sans-serif">\n<rect width="100%" height="100%" fill="white"/>\n'
       + "\n".join(partes) + "\n</svg>\n")

pasta = os.path.dirname(os.path.abspath(__file__))
with tempfile.TemporaryDirectory() as tmp:
    arquivo_svg = os.path.join(tmp, "automato.svg")
    arquivo_png = os.path.join(tmp, "automato.png")
    with open(arquivo_svg, "w", encoding="utf-8") as f:
        f.write(svg)
    subprocess.run(["rsvg-convert", "-o", arquivo_png, arquivo_svg], check=True)
    subprocess.run(["convert", arquivo_png, "-quality", "92", os.path.join(pasta, "automato_completo.jpg")], check=True)
print("docs/automato_completo.jpg gerado")
