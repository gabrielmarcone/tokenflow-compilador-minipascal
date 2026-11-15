package minipascal.sintatico.slr;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Estado do autômato LR(0): um conjunto de itens já fechado (closure),
 * com o número que ele recebe na tabela.
 */
public final class EstadoLR0 {

    private final int numero;
    private final SortedSet<ItemLR0> itens;

    public EstadoLR0(int numero, SortedSet<ItemLR0> itens) {
        this.numero = numero;
        this.itens = Collections.unmodifiableSortedSet(new TreeSet<>(itens));
    }

    public int getNumero() {
        return numero;
    }

    public SortedSet<ItemLR0> getItens() {
        return itens;
    }
}
