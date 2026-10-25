package pt.ulisboa.batchpoint.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** Testes do quadro "publicar e esperar por um valor novo" (fase 6). Gerados pelo Claude. */
class QuadroTest {

    /** Lança uma thread que espera por um valor diferente de {@code anterior} e guarda o que recebeu. */
    private static Thread esperar(Quadro<String> q, String anterior, AtomicReference<String> recebido,
            CountDownLatch acabou) {
        Thread t = new Thread(() -> {
            try {
                recebido.set(q.esperarDiferente(anterior));
                acabou.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    void comecaVazio() {
        assertNull(new Quadro<String>().atual());
    }

    @Test
    void atualDevolveOUltimoPublicado() {
        Quadro<String> q = new Quadro<>();
        q.publicarNovo("a");
        q.publicarNovo("b");
        assertEquals("b", q.atual());
    }

    @Test
    void naoEsperaSeJaHaUmValorDiferente() throws Exception {
        Quadro<String> q = new Quadro<>();
        q.publicarNovo("b");
        assertEquals("b", q.esperarDiferente("a"));
    }

    @Test
    void esperaPeloPrimeiroValorQuandoEstaVazio() throws Exception {
        Quadro<String> q = new Quadro<>();
        AtomicReference<String> recebido = new AtomicReference<>();
        CountDownLatch acabou = new CountDownLatch(1);
        esperar(q, null, recebido, acabou);

        assertTrue(!acabou.await(200, TimeUnit.MILLISECONDS), "não devia acordar com o quadro vazio");
        q.publicarNovo("primeiro");
        assertTrue(acabou.await(2, TimeUnit.SECONDS), "devia acordar depois do publicar");
        assertEquals("primeiro", recebido.get());
    }

    @Test
    void esperaEnquantoOValorForOMesmo() throws Exception {
        Quadro<String> q = new Quadro<>();
        String r1 = new String("rep");     // dois objetos diferentes com o mesmo texto
        String r2 = new String("rep");
        q.publicarNovo(r1);

        AtomicReference<String> recebido = new AtomicReference<>();
        CountDownLatch acabou = new CountDownLatch(1);
        esperar(q, r1, recebido, acabou);

        assertTrue(!acabou.await(200, TimeUnit.MILLISECONDS), "não devia acordar: o valor é o mesmo");
        q.publicarNovo(r2);   // igual em texto, mas outro objeto: conta como novo (compara-se com ==)
        assertTrue(acabou.await(2, TimeUnit.SECONDS), "devia acordar com um objeto novo");
        assertTrue(recebido.get() == r2);
    }

    @Test
    void publicarAcordaTodasAsThreadsAEspera() throws Exception {
        Quadro<String> q = new Quadro<>();
        q.publicarNovo("velho");
        int n = 5;
        CountDownLatch acabou = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            esperar(q, "velho", new AtomicReference<>(), acabou);
        }
        Thread.sleep(100);
        q.publicarNovo("novo");
        assertTrue(acabou.await(2, TimeUnit.SECONDS), "o notifyAll tem de acordar todas");
    }
}
