package pt.ulisboa.batchpoint.coordenador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Testes da Ligacao (fase 6): o teste faz de nó, por um socket local, e confirma
 * o que a ligação regista na repetição. Gerados pelo Claude.
 */
class LigacaoTest {

    /** Liga um "nó" falso a uma Ligacao a correr numa thread. Devolve o canal do lado do nó. */
    private static Canal ligar(Distribuidor d, ServerSocket servidor, AtomicReference<Throwable> erro,
            List<Thread> threads) throws Exception {
        Socket socketNo = new Socket("localhost", servidor.getLocalPort());
        Canal coord = new Canal(servidor.accept());
        Thread t = new Thread(new Ligacao(coord, d, 0));
        t.setUncaughtExceptionHandler((th, e) -> erro.set(e));
        t.setDaemon(true);
        t.start();
        threads.add(t);
        return new Canal(socketNo);
    }

    @Test
    void conversaCompletaRegistaTudoNaRepeticao() throws Exception {
        Tarefa[] tarefas = GeradorDeTarefas.gerar(7, 10, "uniforme", 42);
        Repeticao rep = new Repeticao(tarefas, 1);
        Distribuidor d = new Distribuidor(3);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();

        try (ServerSocket servidor = new ServerSocket(0);
             Canal no = ligar(d, servidor, erro, threads)) {
            d.publicar(rep);
            no.enviarHello(1, 2);

            long xorEsperado = 0;
            long somaDuracoes = 0;
            int lotes = 0;
            int recebidasTotal = 0;
            while (recebidasTotal < tarefas.length) {
                no.enviarPedido(3);
                assertEquals(Protocolo.TASKS, no.lerTipo());
                List<Tarefa> recebidas = no.lerTarefas();
                lotes++;
                recebidasTotal += recebidas.size();
                List<Resultado> resultados = new ArrayList<>();
                for (Tarefa t : recebidas) {
                    long valor = 1000L + t.id();          // um valor qualquer, conhecido
                    resultados.add(new Resultado(t.id(), valor, 5));
                    xorEsperado ^= valor;
                    somaDuracoes += 5;
                }
                no.enviarResultados(resultados);
            }
            assertTrue(rep.cDownLatch.await(2, java.util.concurrent.TimeUnit.SECONDS),
                    "o latch devia chegar a 0 depois do último RESULTS");

            assertEquals(3, lotes, "7 tarefas em lotes de 3 = 3 lotes");
            assertTrue(rep.completa(), "cada tarefa tem de ter chegado uma vez");
            assertEquals(xorEsperado, rep.checkSum.get());
            assertEquals(somaDuracoes, rep.somaDuracaoNs.get());
            assertEquals(3 * 3, rep.mensagens.get(), "3 por lote: REQUEST, TASKS e RESULTS");
            assertEquals(2 * (5 + 3 * 12) + (5 + 1 * 12), rep.bytesEnviados.get(), "bytes dos três TASKS");

            d.terminar();
            no.enviarPedido(3);
            assertEquals(Protocolo.DONE, no.lerTipo());
            threads.get(0).join(2_000);
            assertTrue(!threads.get(0).isAlive(), "a ligação devia terminar depois do DONE");
            assertTrue(erro.get() == null, "erro na ligação: " + erro.get());
        }
    }

    @Test
    void primeiraMensagemTemDeSerHello() throws Exception {
        Distribuidor d = new Distribuidor(1);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();

        try (ServerSocket servidor = new ServerSocket(0);
             Canal no = ligar(d, servidor, erro, threads)) {
            no.enviarPedido(1);                          // sem HELLO
            threads.get(0).join(2_000);
            assertTrue(!threads.get(0).isAlive(), "a ligação devia parar");
            assertTrue(erro.get() instanceof IllegalStateException, "esperava IllegalStateException, veio " + erro.get());
        }
    }

    @Test
    void mensagemDesconhecidaParaALigacao() throws Exception {
        Distribuidor d = new Distribuidor(1);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();

        try (ServerSocket servidor = new ServerSocket(0);
             Canal no = ligar(d, servidor, erro, threads)) {
            no.enviarHello(1, 2);
            no.enviarDone();                             // o coordenador nunca recebe DONE
            threads.get(0).join(2_000);
            assertTrue(!threads.get(0).isAlive(), "a ligação devia parar");
            assertTrue(erro.get() instanceof IllegalStateException, "esperava IllegalStateException, veio " + erro.get());
        }
    }
}
