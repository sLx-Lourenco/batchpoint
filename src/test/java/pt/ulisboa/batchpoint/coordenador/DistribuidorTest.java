package pt.ulisboa.batchpoint.coordenador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/** Testes do distribuidor: a reserva de tarefas e a espera entre repetições (fase 6). Gerados pelo Claude. */
class DistribuidorTest {

    private static Repeticao repeticao(int n) {
        return new Repeticao(GeradorDeTarefas.gerar(n, 10, "uniforme", 42));
    }

    private static List<Integer> ids(Entrega e) {
        List<Integer> v = new ArrayList<>();
        for (Tarefa t : e.listaTarefas()) {
            v.add(t.id());
        }
        return v;
    }

    // ---------- tamanho de cada entrega ----------

    @Test
    void lotesSeguidosEUltimoMaisPequeno() throws Exception {
        Distribuidor d = new Distribuidor(4);
        d.publicar(repeticao(10));
        assertEquals(List.of(0, 1, 2, 3), ids(d.obterTarefas(4)));
        assertEquals(List.of(4, 5, 6, 7), ids(d.obterTarefas(4)));
        assertEquals(List.of(8, 9), ids(d.obterTarefas(4)));
    }

    @Test
    void naPlanaCadaPedidoLevaUmaTarefa() throws Exception {
        Distribuidor d = new Distribuidor(1);
        d.publicar(repeticao(5));
        assertEquals(1, d.obterTarefas(100).listaTarefas().size(), "o lote 1 manda, mesmo que o nó peça mais");
    }

    @Test
    void pedidoMaisPequenoQueOLote() throws Exception {
        Distribuidor d = new Distribuidor(50);
        d.publicar(repeticao(100));
        assertEquals(3, d.obterTarefas(3).listaTarefas().size());
    }

    @Test
    void entregaDizAQueRepeticaoPertence() throws Exception {
        Distribuidor d = new Distribuidor(2);
        Repeticao r = repeticao(4);
        d.publicar(r);
        assertTrue(d.obterTarefas(2).rep() == r);
    }

    // ---------- fim ----------

    @Test
    void depoisDoTerminarDevolveNull() throws Exception {
        Distribuidor d = new Distribuidor(1);
        d.publicar(repeticao(1));
        d.obterTarefas(1);
        d.terminar();
        assertNull(d.obterTarefas(1));
        assertNull(d.obterTarefas(1));
    }

    // ---------- esperas ----------

    @Test
    void esperaPelaPrimeiraRepeticao() throws Exception {
        Distribuidor d = new Distribuidor(3);
        AtomicReference<Entrega> recebida = new AtomicReference<>();
        CountDownLatch acabou = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                recebida.set(d.obterTarefas(3));
                acabou.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();

        assertTrue(!acabou.await(200, TimeUnit.MILLISECONDS), "não pode receber nada antes da primeira repetição");
        d.publicar(repeticao(5));
        assertTrue(acabou.await(2, TimeUnit.SECONDS), "devia acordar com a primeira repetição");
        assertEquals(List.of(0, 1, 2), ids(recebida.get()));
    }

    @Test
    void repeticaoEsgotadaEsperaPelaSeguinte() throws Exception {
        Distribuidor d = new Distribuidor(2);
        d.publicar(repeticao(2));
        d.obterTarefas(2);                        // esgota a primeira

        Repeticao segunda = repeticao(2);
        AtomicReference<Entrega> recebida = new AtomicReference<>();
        CountDownLatch acabou = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                recebida.set(d.obterTarefas(2));
                acabou.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();

        assertTrue(!acabou.await(200, TimeUnit.MILLISECONDS), "a primeira está esgotada: tem de esperar");
        d.publicar(segunda);
        assertTrue(acabou.await(2, TimeUnit.SECONDS));
        assertTrue(recebida.get().rep() == segunda, "as tarefas têm de vir da repetição nova");
    }

    @Test
    void terminarAcordaQuemEstaAEspera() throws Exception {
        Distribuidor d = new Distribuidor(1);
        d.publicar(repeticao(1));
        d.obterTarefas(1);
        CountDownLatch acabou = new CountDownLatch(1);
        AtomicReference<Entrega> recebida = new AtomicReference<>(new Entrega(null, List.of()));
        Thread t = new Thread(() -> {
            try {
                recebida.set(d.obterTarefas(1));
                acabou.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();
        Thread.sleep(100);
        d.terminar();
        assertTrue(acabou.await(2, TimeUnit.SECONDS), "o terminar tem de acordar quem espera");
        assertNull(recebida.get());
    }

    // ---------- concorrência ----------

    @Test
    void comVariasThreadsCadaTarefaEntregueUmaVez() throws Exception {
        int n = 10_000;
        int lote = 7;
        int nThreads = 6;
        Distribuidor d = new Distribuidor(lote);
        AtomicIntegerArray entregues = new AtomicIntegerArray(n);
        List<String> erros = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < nThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    Entrega e;
                    while ((e = d.obterTarefas(lote)) != null) {
                        if (e.listaTarefas().size() > lote) {
                            erros.add("lote de " + e.listaTarefas().size());
                        }
                        for (Tarefa tarefa : e.listaTarefas()) {
                            entregues.incrementAndGet(tarefa.id());
                        }
                    }
                } catch (InterruptedException ex) {
                    erros.add("interrompida");
                }
            });
            t.start();
            threads.add(t);
        }

        d.publicar(repeticao(n));
        Thread.sleep(300);       // tempo para esgotarem a repetição
        d.terminar();
        for (Thread t : threads) {
            t.join(5_000);
            assertTrue(!t.isAlive(), "uma thread não terminou depois do terminar()");
        }

        assertTrue(erros.isEmpty(), erros.toString());
        for (int i = 0; i < n; i++) {
            assertEquals(1, entregues.get(i), "tarefa " + i);
        }
    }
}
