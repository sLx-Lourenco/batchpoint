package pt.ulisboa.batchpoint.cargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Testes da carga sintética (fase 3). Gerados pelo Claude. */
class SinteticaTest {

    private final Carga carga = new Sintetica();

    /** Implementação de referência, escrita à parte, com o trio clássico (13, 7, 17). */
    private static long referencia(int id, long iteracoes) {
        long x = id + 1L;
        for (long i = 0; i < iteracoes; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
        }
        return x;
    }

    @Test
    void mesmaTarefaDaSempreOMesmoResultado() {
        long a = carga.executar(42, 10_000);
        long b = carga.executar(42, 10_000);
        assertEquals(a, b);
    }

    @Test
    void doisObjetosDaoOMesmoResultado() {
        // O coordenador e os nós criam cada um a sua carga: têm de concordar.
        assertEquals(new Sintetica().executar(7, 5_000), new Sintetica().executar(7, 5_000));
    }

    @Test
    void idsDiferentesDaoResultadosDiferentes() {
        Set<Long> vistos = new HashSet<>();
        for (int id = 0; id < 1_000; id++) {
            assertTrue(vistos.add(carga.executar(id, 100)), "resultado repetido no id " + id);
        }
    }

    @Test
    void iteracoesDiferentesDaoResultadosDiferentes() {
        assertNotEquals(carga.executar(3, 1_000), carga.executar(3, 1_001));
    }

    @Test
    void zeroIteracoesNaoRebentaENaoDaZero() {
        // Com 0 iterações devolve o estado inicial, que nunca pode ser 0.
        assertNotEquals(0L, carga.executar(0, 0));
    }

    @Test
    void idZeroNuncaFicaPresoEmZero() {
        assertNotEquals(0L, carga.executar(0, 1_000));
    }

    @Test
    void idMaximoNaoDaVoltaParaZero() {
        // id + 1 tem de ser feito em long: em int, Integer.MAX_VALUE + 1 dava negativo.
        assertNotEquals(0L, carga.executar(Integer.MAX_VALUE, 10));
        assertEquals(referencia(Integer.MAX_VALUE, 10), carga.executar(Integer.MAX_VALUE, 10));
    }

    @Test
    void bateCertoComAReferencia() {
        // Apanha constantes trocadas no Config, >> em vez de >>> ou a ordem dos passos.
        for (int id : new int[] {0, 1, 2, 1_000, 123_456}) {
            for (long it : new long[] {1, 2, 3, 5_971}) {
                assertEquals(referencia(id, it), carga.executar(id, it), "id=" + id + " iteracoes=" + it);
            }
        }
    }
}
