package pt.ulisboa.batchpoint.no.plano;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.cargas.Sintetica;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Testes do nó plano (fase 6, movido para no/plano na fase 9): o teste faz de coordenador
 * num socket local. Cada thread do nó abre a sua ligação, pede uma tarefa de cada vez e
 * termina com DONE. O coordenador falso tem tempo limite de leitura, por isso um nó
 * encravado faz o teste falhar em vez de ficar pendurado. Gerados pelo Claude.
 */
class NoPlanoTest {

    private static Canal aceitar(ServerSocket servidor) throws IOException {
        Socket s = servidor.accept();
        s.setSoTimeout(5_000);
        return new Canal(s);
    }

    private static InetSocketAddress endereco(ServerSocket servidor) {
        return InetSocketAddress.createUnresolved("localhost", servidor.getLocalPort());
    }

    @Test
    void cadaThreadAbreASuaLigacaoEPedeUmaTarefa() throws Exception {
        Sintetica s = new Sintetica();
        int threads = 3, rondas = 4;
        try (ServerSocket servidor = new ServerSocket(0)) {
            AtomicReference<Throwable> erro = new AtomicReference<>();
            Thread no = new Thread(() -> {
                try {
                    NoPlano.correr(endereco(servidor), 5, threads, s);
                } catch (Throwable e) {
                    erro.set(e);
                }
            });
            no.setDaemon(true);
            no.start();

            List<Canal> canais = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Canal c = aceitar(servidor);
                canais.add(c);
                assertEquals(Protocolo.HELLO, c.lerTipo());
                assertEquals(5, c.lerInt(), "id do nó");
                assertEquals(threads, c.lerInt(), "threads do nó");
            }

            // Serve as ligações à vez: cada uma recebe uma tarefa por ronda.
            Set<Integer> recebidas = new HashSet<>();
            int id = 0;
            for (int r = 0; r < rondas; r++) {
                for (Canal c : canais) {
                    assertEquals(Protocolo.REQUEST, c.lerTipo());
                    assertEquals(1, c.lerInt(), "na plana pede-se sempre 1");
                    c.enviarTarefas(List.of(new Tarefa(id, 300)));
                    assertEquals(Protocolo.RESULTS, c.lerTipo());
                    List<Resultado> rs = c.lerResultados();
                    assertEquals(1, rs.size());
                    assertEquals(id, rs.get(0).id());
                    assertEquals(s.executar(id, 300), rs.get(0).valor(), "valor da tarefa " + id);
                    recebidas.add(id);
                    id++;
                }
            }
            assertEquals(threads * rondas, recebidas.size());

            for (Canal c : canais) {
                assertEquals(Protocolo.REQUEST, c.lerTipo());
                c.lerInt();
                c.enviarDone();
            }
            no.join(5_000);
            assertTrue(!no.isAlive(), "o correr devia acabar depois dos DONE (join das threads)");
            assertTrue(erro.get() == null, "erro no nó: " + erro.get());
            for (Canal c : canais) c.close();
        }
    }

    @Test
    void loteComVariasTarefasVoltaInteiro() throws Exception {
        // O coordenador nunca manda mais de 1 na plana, mas o work trata o que vier.
        try (ServerSocket servidor = new ServerSocket(0)) {
            Thread no = arrancarWork(servidor, new AtomicReference<>());
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
                c.enviarTarefas(List.of(new Tarefa(0, 10), new Tarefa(1, 10), new Tarefa(2, 10)));
                assertEquals(Protocolo.RESULTS, c.lerTipo());
                assertEquals(3, c.lerResultados().size());
                c.lerTipo(); c.lerInt();
                c.enviarDone();
                no.join(5_000);
                assertTrue(!no.isAlive());
            }
        }
    }

    @Test
    void tipoInesperadoDaErro() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            AtomicReference<Throwable> erro = new AtomicReference<>();
            Thread no = arrancarWork(servidor, erro);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
                c.enviarHello(9, 9);                    // o nó nunca recebe HELLO
                no.join(5_000);
                assertTrue(!no.isAlive(), "o work devia parar");
                assertTrue(erro.get() instanceof IllegalStateException,
                        "esperava IllegalStateException, veio " + erro.get());
            }
        }
    }

    @Test
    void coordenadorQueCaiDaErroDeRede() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            AtomicReference<Throwable> erro = new AtomicReference<>();
            Thread no = arrancarWork(servidor, erro);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
            }                                           // fecha sem responder
            no.join(5_000);
            assertTrue(!no.isAlive(), "o work devia parar");
            assertTrue(erro.get() instanceof RuntimeException && erro.get().getCause() instanceof IOException,
                    "esperava RuntimeException com IOException, veio " + erro.get());
        }
    }

    /** Corre o work de uma só thread, guardando a exceção que o fizer parar. */
    private static Thread arrancarWork(ServerSocket servidor, AtomicReference<Throwable> erro) {
        Thread t = new Thread(() -> NoPlano.work(endereco(servidor), 1, 1, new Sintetica()));
        t.setUncaughtExceptionHandler((th, e) -> erro.set(e));
        t.setDaemon(true);
        t.start();
        return t;
    }
}
