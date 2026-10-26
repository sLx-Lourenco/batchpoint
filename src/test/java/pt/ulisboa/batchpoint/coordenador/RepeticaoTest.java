package pt.ulisboa.batchpoint.coordenador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/** Testes do estado de uma repetição (fase 6). Gerados pelo Claude. */
class RepeticaoTest {

    private static Tarefa[] tarefas(int n) {
        return GeradorDeTarefas.gerar(n, 10, "uniforme", 42);
    }

    @Test
    void comecaAZeroEComOLatchDoTamanhoCerto() {
        Repeticao r = new Repeticao(tarefas(50), 1);
        assertEquals(0, r.proxima.get());
        assertEquals(50, r.cDownLatch.getCount());
        assertEquals(50, r.recebidos.length());
        assertEquals(0, r.checkSum.get());
        assertEquals(0, r.mensagens.get());
        assertEquals(0, r.bytesEnviados.get());
        assertEquals(0, r.somaDuracaoNs.get());
    }

    @Test
    void incompletaSemResultados() {
        assertTrue(!new Repeticao(tarefas(3), 1).completa());
    }

    @Test
    void completaQuandoCadaTarefaChegouUmaVez() {
        Repeticao r = new Repeticao(tarefas(3), 1);
        for (int i = 0; i < 3; i++) {
            r.recebidos.incrementAndGet(i);
        }
        assertTrue(r.completa());
    }

    @Test
    void incompletaComUmaTarefaEmFalta() {
        Repeticao r = new Repeticao(tarefas(3), 1);
        r.recebidos.incrementAndGet(0);
        r.recebidos.incrementAndGet(2);
        assertTrue(!r.completa());
    }

    @Test
    void incompletaComUmaTarefaRepetida() {
        Repeticao r = new Repeticao(tarefas(3), 1);
        for (int i = 0; i < 3; i++) {
            r.recebidos.incrementAndGet(i);
        }
        r.recebidos.incrementAndGet(1);      // a tarefa 1 chegou duas vezes
        assertTrue(!r.completa());
    }

    @Test
    void repeticaoVaziaEstaCompleta() {
        assertTrue(new Repeticao(new Tarefa[0], 1).completa());
    }
}
