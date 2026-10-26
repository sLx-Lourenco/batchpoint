package pt.ulisboa.batchpoint.no.hierarquico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Testes do lote local (fase 9): o estado inicial, a pílula e a lista de resultados
 * partilhada pelas threads de cálculo. Gerados pelo Claude.
 */
class LoteLocalTest {

    @Test
    void comecaPorZeroComOLatchNoTamanho() {
        LoteLocal l = new LoteLocal(List.of(new Tarefa(0, 1), new Tarefa(1, 1), new Tarefa(2, 1)));
        assertEquals(3, l.tarefas.size());
        assertEquals(0, l.proxima.get(), "a primeira tarefa a tirar é a 0");
        assertEquals(3, l.cDownLatch.getCount(), "o latch conta uma vez por tarefa");
        assertTrue(l.resultados.isEmpty(), "ainda sem resultados");
    }

    @Test
    void poisonEVaziaEUnica() {
        assertEquals(0, LoteLocal.POISON.tarefas.size(), "a pílula não tem tarefas");
        assertEquals(0, LoteLocal.POISON.cDownLatch.getCount());
        assertTrue(LoteLocal.POISON != new LoteLocal(List.of()),
                "um lote vazio qualquer não é a pílula: compara-se pela identidade");
    }

    @Test
    void resultadosAguentamEscritasEmParalelo() throws Exception {
        int threads = 8, porThread = 20_000;
        LoteLocal l = new LoteLocal(List.of());
        List<Thread> ts = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < porThread; i++) l.resultados.add(new Resultado(i, i, 0));
            });
            ts.add(th);
            th.start();
        }
        for (Thread th : ts) th.join();
        assertEquals(threads * porThread, l.resultados.size(),
                "com um ArrayList simples perdiam-se elementos (ou rebentava)");
    }
}
