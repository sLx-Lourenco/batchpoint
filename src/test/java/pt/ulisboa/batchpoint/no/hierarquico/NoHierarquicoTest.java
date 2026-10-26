package pt.ulisboa.batchpoint.no.hierarquico;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.cargas.Carga;
import pt.ulisboa.batchpoint.cargas.Sintetica;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Testes do nó hierárquico (fase 9): o teste faz de coordenador num socket local e
 * o nó corre numa thread. Confirma o protocolo, que cada lote volta completo num só
 * RESULTS e que o nó termina (sem threads penduradas) tanto com DONE como com erro.
 * O coordenador falso tem tempo limite de leitura, por isso um nó encravado faz o teste falhar.
 * Gerados pelo Claude.
 */
class NoHierarquicoTest {

    /** O nó a correr numa thread, com a exceção (se houver) guardada. */
    private static final class NoEmThread {
        final Thread thread;
        final AtomicReference<Throwable> erro = new AtomicReference<>();
        final Set<Thread> antes = new HashSet<>(Thread.getAllStackTraces().keySet());

        NoEmThread(int porta, int id, int threads, int lote, Carga carga) {
            InetSocketAddress coord = InetSocketAddress.createUnresolved("localhost", porta);
            thread = new Thread(() -> {
                try {
                    NoHierarquico.correr(coord, id, threads, lote, carga);
                } catch (Throwable e) {
                    erro.set(e);
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        /** Espera que o nó e todas as threads que ele criou terminem; falha se alguma ficar pendurada. */
        void esperarFim() throws InterruptedException {
            thread.join(5_000);
            assertTrue(!thread.isAlive(), "o nó devia ter terminado");
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (!antes.contains(t) && t != thread) {
                    t.join(2_000);
                    assertTrue(!t.isAlive(), "thread de cálculo pendurada: " + t.getName());
                }
            }
        }
    }

    /** Aceita o nó, com tempo limite: se o nó encravar, o teste falha em vez de ficar pendurado. */
    private static Canal aceitar(ServerSocket servidor) throws IOException {
        Socket s = servidor.accept();
        s.setSoTimeout(5_000);
        return new Canal(s);
    }

    private static List<Tarefa> tarefas(int de, int n, long parametro) {
        List<Tarefa> l = new ArrayList<>();
        for (int i = de; i < de + n; i++) l.add(new Tarefa(i, parametro));
        return l;
    }

    @Test
    void conversaCompletaComLotesEDone() throws Exception {
        Sintetica s = new Sintetica();
        try (ServerSocket servidor = new ServerSocket(0)) {
            NoEmThread no = new NoEmThread(servidor.getLocalPort(), 7, 3, 100, s);
            try (Canal c = aceitar(servidor)) {
                assertEquals(Protocolo.HELLO, c.lerTipo());
                assertEquals(7, c.lerInt(), "id do nó");
                assertEquals(3, c.lerInt(), "threads do nó");

                int[] tamanhos = {100, 100, 50};
                int proximoId = 0;
                for (int n : tamanhos) {
                    assertEquals(Protocolo.REQUEST, c.lerTipo());
                    assertEquals(100, c.lerInt(), "o nó pede o tamanho do lote");
                    List<Tarefa> lote = tarefas(proximoId, n, 500);
                    c.enviarTarefas(lote);

                    assertEquals(Protocolo.RESULTS, c.lerTipo(), "depois do TASKS vêm os resultados");
                    List<Resultado> rs = c.lerResultados();
                    assertEquals(n, rs.size(), "o lote inteiro num só RESULTS");
                    Set<Integer> ids = new HashSet<>();
                    for (Resultado r : rs) {
                        assertTrue(ids.add(r.id()), "tarefa " + r.id() + " repetida");
                        assertEquals(s.executar(r.id(), 500), r.valor(), "valor da tarefa " + r.id());
                        assertTrue(r.duracaoNs() >= 0, "duração não negativa");
                    }
                    for (Tarefa t : lote) assertTrue(ids.contains(t.id()), "falta a tarefa " + t.id());
                    proximoId += n;
                }

                assertEquals(Protocolo.REQUEST, c.lerTipo());
                c.lerInt();
                c.enviarDone();
                no.esperarFim();
                assertTrue(no.erro.get() == null, "erro no nó: " + no.erro.get());
            }
        }
    }

    @Test
    void esperaPeloLoteTodoAntesDeResponder() throws Exception {
        // A tarefa 0 é lenta: se o nó respondesse antes do fim, vinha um RESULTS incompleto.
        Carga lenta = (id, p) -> {
            if (id == 0) dormir(300);
            return id * 10L;
        };
        try (ServerSocket servidor = new ServerSocket(0)) {
            NoEmThread no = new NoEmThread(servidor.getLocalPort(), 1, 2, 20, lenta);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
                c.enviarTarefas(tarefas(0, 20, 0));
                assertEquals(Protocolo.RESULTS, c.lerTipo());
                List<Resultado> rs = c.lerResultados();
                assertEquals(20, rs.size(), "a resposta só sai com as 20 tarefas feitas");

                c.lerTipo(); c.lerInt();
                c.enviarDone();
                no.esperarFim();
                assertTrue(no.erro.get() == null, "erro no nó: " + no.erro.get());
            }
        }
    }

    @Test
    void todasAsThreadsDeCalculoTrabalham() throws Exception {
        Set<String> nomes = ConcurrentHashMap.newKeySet();
        Carga regista = (id, p) -> {
            nomes.add(Thread.currentThread().getName());
            dormir(10);
            return id;
        };
        try (ServerSocket servidor = new ServerSocket(0)) {
            NoEmThread no = new NoEmThread(servidor.getLocalPort(), 1, 3, 60, regista);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
                c.enviarTarefas(tarefas(0, 60, 0));
                c.lerTipo(); c.lerResultados();
                c.lerTipo(); c.lerInt();
                c.enviarDone();
                no.esperarFim();
            }
        }
        assertEquals(3, nomes.size(), "as 3 threads de cálculo deviam ter tirado tarefas: " + nomes);
        assertTrue(!nomes.contains(Thread.currentThread().getName()), "o teste não executa tarefas");
    }

    @Test
    void variosLotesSeguidosReusamAsThreads() throws Exception {
        // 50 lotes de 1 tarefa: cada lote novo tem de acordar as threads que esperam no quadro.
        try (ServerSocket servidor = new ServerSocket(0)) {
            NoEmThread no = new NoEmThread(servidor.getLocalPort(), 1, 2, 1, (id, p) -> id + 1L);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                for (int i = 0; i < 50; i++) {
                    assertEquals(Protocolo.REQUEST, c.lerTipo());
                    assertEquals(1, c.lerInt());
                    c.enviarTarefas(List.of(new Tarefa(i, 0)));
                    assertEquals(Protocolo.RESULTS, c.lerTipo());
                    List<Resultado> rs = c.lerResultados();
                    assertEquals(1, rs.size());
                    assertEquals(i + 1L, rs.get(0).valor(), "lote " + i);
                }
                c.lerTipo(); c.lerInt();
                c.enviarDone();
                no.esperarFim();
                assertTrue(no.erro.get() == null, "erro no nó: " + no.erro.get());
            }
        }
    }

    @Test
    void coordenadorQueCaiNaoDeixaThreadsPenduradas() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            NoEmThread no = new NoEmThread(servidor.getLocalPort(), 1, 2, 10, (id, p) -> id);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
            }                                           // fecha a ligação sem responder
            no.esperarFim();
            assertTrue(no.erro.get() instanceof IOException,
                    "esperava IOException, veio " + no.erro.get());
        }
    }

    @Test
    void tipoInesperadoDaErroETermina() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            NoEmThread no = new NoEmThread(servidor.getLocalPort(), 1, 2, 10, (id, p) -> id);
            try (Canal c = aceitar(servidor)) {
                c.lerTipo(); c.lerInt(); c.lerInt();
                c.lerTipo(); c.lerInt();
                c.enviarHello(9, 9);                    // o nó nunca recebe HELLO
                no.esperarFim();
                assertTrue(no.erro.get() instanceof IllegalStateException,
                        "esperava IllegalStateException, veio " + no.erro.get());
            }
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
