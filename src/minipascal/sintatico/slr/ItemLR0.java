package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;

/**
 * Item LR(0): uma producao com um ponto marcando quanto do corpo ja foi
 * reconhecido. Ex.: TERMO -> TERMO . * FATOR (ponto = 1).
 * A ordem natural (producao, depois ponto) da uma ordem fixa aos itens de
 * cada estado, o que torna a numeracao dos estados sempre a mesma.
 */
public final class ItemLR0 implements Comparable<ItemLR0> {

    private final int producao;
    private final int ponto;

    public ItemLR0(int producao, int ponto) {
        this.producao = producao;
        this.ponto = ponto;
    }

    public int getProducao() {
        return producao;
    }

    public int getPonto() {
        return ponto;
    }

    // Simbolo logo apos o ponto, ou null se o item esta completo.
    public String simboloAposPonto(Gramatica g) {
        Producao p = g.getProducao(producao);
        return ponto < p.tamanho() ? p.getCorpo().get(ponto) : null;
    }

    // Ponto no fim do corpo: o item pede uma reducao.
    public boolean isCompleto(Gramatica g) {
        return ponto == g.getProducao(producao).tamanho();
    }

    // O mesmo item com o ponto uma posicao a frente (usado no goto).
    public ItemLR0 avancar() {
        return new ItemLR0(producao, ponto + 1);
    }

    // "TERMO -> TERMO . * FATOR"
    public String descrever(Gramatica g) {
        Producao p = g.getProducao(producao);
        StringBuilder sb = new StringBuilder(p.getCabeca()).append(" ->");
        for (int i = 0; i < p.tamanho(); i++) {
            if (i == ponto) {
                sb.append(" •");
            }
            sb.append(' ').append(p.getCorpo().get(i));
        }
        if (ponto == p.tamanho()) {
            sb.append(" •");
        }
        return sb.toString();
    }

    @Override
    // Ordena por producao e depois pela posicao do ponto.
    public int compareTo(ItemLR0 outro) {
        if (producao != outro.producao) {
            return Integer.compare(producao, outro.producao);
        }
        return Integer.compare(ponto, outro.ponto);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemLR0)) {
            return false;
        }
        ItemLR0 outro = (ItemLR0) o;
        return producao == outro.producao && ponto == outro.ponto;
    }

    @Override
    public int hashCode() {
        return 31 * producao + ponto;
    }
}
