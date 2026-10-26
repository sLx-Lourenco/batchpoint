package pt.ulisboa.batchpoint.cargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Testes do gerador de tarefas do Mandelbrot (fase 10): um bloco por tarefa, por ordem
 * de linhas, e só lados que dividem 4096. Gerados pelo Claude.
 */
class GeradorMandelbrotTest {

    @Test
    void numeroDeTarefasCertoParaCadaLado() {
        assertEquals(64, GeradorDeTarefas.gerarMandelbrot(512).length);
        assertEquals(1_024, GeradorDeTarefas.gerarMandelbrot(128).length);
        assertEquals(16_384, GeradorDeTarefas.gerarMandelbrot(32).length);
        assertEquals(262_144, GeradorDeTarefas.gerarMandelbrot(8).length);
        assertEquals(1, GeradorDeTarefas.gerarMandelbrot(4096).length, "a imagem inteira num só bloco");
    }

    @Test
    void idsSeguidosEParametroIgualAoLado() {
        Tarefa[] ts = GeradorDeTarefas.gerarMandelbrot(128);
        for (int i = 0; i < ts.length; i++) {
            assertEquals(i, ts[i].id(), "a tarefa na posição " + i);
            assertEquals(128, ts[i].parametro(), "o parâmetro é o lado");
        }
    }

    @Test
    void ladoQueNaoDivideARecusado() {
        for (int lado : new int[] {100, 3000, 8192}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> GeradorDeTarefas.gerarMandelbrot(lado));
            assertTrue(e.getMessage().contains(String.valueOf(lado)), e.getMessage());
        }
    }

    @Test
    void ladoZeroOuNegativoRecusado() {
        // Com lado 0, sem a verificação de <= 0, o % rebentava com ArithmeticException.
        assertThrows(IllegalArgumentException.class, () -> GeradorDeTarefas.gerarMandelbrot(0));
        assertThrows(IllegalArgumentException.class, () -> GeradorDeTarefas.gerarMandelbrot(-8));
    }
}
