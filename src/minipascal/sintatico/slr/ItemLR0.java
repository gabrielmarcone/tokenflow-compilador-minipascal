package minipascal.sintatico.slr;

import minipascal.sintatico.gramatica.Gramatica;
import minipascal.sintatico.gramatica.Producao;

/**
 * Item LR(0): uma produção com um ponto marcando quanto do corpo já foi
 * reconhecido. Ex.: TERMO -> TERMO • * FATOR (ponto = 1).
 * A ordem natural (produção, depois ponto) dá uma ordem fixa aos itens de
 * cada estado, o que torna a numeração dos estados sempre a mesma.
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

    // Símbolo logo após o ponto, ou null se o item está completo.
    public String simboloAposPonto(Gramatica g) {
        Producao p = g.getProducao(producao);
        return ponto < p.tamanho() ? p.getCorpo().get(ponto) : null;
    }

    public boolean isCompleto(Gramatica g) {
        return ponto == g.getProducao(producao).tamanho();
    }

    public ItemLR0 avancar() {
        return new ItemLR0(producao, ponto + 1);
    }

    // "TERMO -> TERMO • * FATOR"
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
