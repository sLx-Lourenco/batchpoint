package pt.ulisboa.batchpoint.coordenador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Testes da instrumentação (fase 11): cada Ligacao soma as durações na sua posição do
 * ocupadoPorLigacao, antes do countDown, e guarda as threads do HELLO. O teste faz de nó
 * por um socket local. Gerados pelo Claude.
 */
class InstrumentacaoTest {

    /** Uma Ligacao a correr numa thread, com o canal do lado do "nó". */
    private record Par(Ligacao ligacao, Canal no, Thread thread) {}

    private static Par ligar(ServerSocket servidor, Distribuidor d, int indice,
            AtomicReference<Throwable> erro) throws Exception {
        Socket socketNo = new Socket("localhost", servidor.getLocalPort());
        Socket s = servidor.accept();
        Ligacao l = new Ligacao(new Canal(s), d, indice);
        Thread t = new Thread(l);
        t.setUncaughtExceptionHandler((th, e) -> erro.set(e));
        t.setDaemon(true);
        t.start();
        socketNo.setSoTimeout(5_000);
        return new Par(l, new Canal(socketNo), t);
    }

    /** O "nó" pede um lote e responde com a duração dada em cada tarefa. Devolve quantas fez. */
    private static int fazerUmLote(Canal no, int pedido, long duracao) throws Exception {
        no.enviarPedido(pedido);
        assertEquals(Protocolo.TASKS, no.lerTipo());
        List<Tarefa> ts = no.lerTarefas();
        List<Resultado> rs = new ArrayList<>();
        for (Tarefa t : ts) rs.add(new Resultado(t.id(), 0, duracao));
        no.enviarResultados(rs);
        return ts.size();
    }

    @Test
    void duracoesVaoParaAPosicaoDaLigacao() throws Exception {
        Tarefa[] tarefas = GeradorDeTarefas.gerar(10, 1, "uniforme", 42);
        Repeticao rep = new Repeticao(tarefas, 3);
        Distribuidor d = new Distribuidor(10);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        try (ServerSocket servidor = new ServerSocket(0)) {
            Par p = ligar(servidor, d, 2, erro);
            d.publicar(rep);
            p.no().enviarHello(1, 2);
            assertEquals(10, fazerUmLote(p.no(), 10, 7));
            assertTrue(rep.cDownLatch.await(2, TimeUnit.SECONDS));

            assertEquals(0, rep.ocupadoPorLigacao.get(0), "a ligação 0 não fez nada");
            assertEquals(0, rep.ocupadoPorLigacao.get(1), "a ligação 1 não fez nada");
            assertEquals(70, rep.ocupadoPorLigacao.get(2), "10 tarefas × 7 ns na ligação 2");
            assertTrue(erro.get() == null, "erro na ligação: " + erro.get());
            p.no().close();
        }
    }

    @Test
    void duasLigacoesSomamSeparadoEOTotalBate() throws Exception {
        Tarefa[] tarefas = GeradorDeTarefas.gerar(20, 1, "uniforme", 42);
        Repeticao rep = new Repeticao(tarefas, 2);
        Distribuidor d = new Distribuidor(5);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        try (ServerSocket servidor = new ServerSocket(0)) {
            Par a = ligar(servidor, d, 0, erro);
            Par b = ligar(servidor, d, 1, erro);
            d.publicar(rep);
            a.no().enviarHello(1, 1);
            b.no().enviarHello(2, 1);

            // A ligação 0 faz 3 lotes de 5 com 100 ns; a 1 faz o último lote com 1000 ns.
            int feitasA = 0;
            for (int i = 0; i < 3; i++) feitasA += fazerUmLote(a.no(), 5, 100);
            int feitasB = fazerUmLote(b.no(), 5, 1_000);
            assertEquals(20, feitasA + feitasB);
            assertTrue(rep.cDownLatch.await(2, TimeUnit.SECONDS));

            assertEquals(15 * 100, rep.ocupadoPorLigacao.get(0));
            assertEquals(5 * 1_000, rep.ocupadoPorLigacao.get(1));
            assertEquals(rep.somaDuracaoNs.get(),
                    rep.ocupadoPorLigacao.get(0) + rep.ocupadoPorLigacao.get(1),
                    "a soma por ligação tem de dar o total");
            assertTrue(erro.get() == null, "erro na ligação: " + erro.get());
            a.no().close();
            b.no().close();
        }
    }

    @Test
    void ocupadoJaEstaCompletoQuandoOLatchChegaAZero() throws Exception {
        // O coordenador lê o ocupado logo a seguir ao await: a soma tem de vir antes do countDown.
        for (int vez = 0; vez < 50; vez++) {
            Tarefa[] tarefas = GeradorDeTarefas.gerar(50, 1, "uniforme", 42);
            Repeticao rep = new Repeticao(tarefas, 1);
            Distribuidor d = new Distribuidor(50);
            AtomicReference<Throwable> erro = new AtomicReference<>();
            try (ServerSocket servidor = new ServerSocket(0)) {
                Par p = ligar(servidor, d, 0, erro);
                d.publicar(rep);
                p.no().enviarHello(1, 1);
                fazerUmLote(p.no(), 50, 3);
                assertTrue(rep.cDownLatch.await(2, TimeUnit.SECONDS));
                assertEquals(150, rep.ocupadoPorLigacao.get(0), "vez " + vez);
                p.no().close();
            }
        }
    }

    @Test
    void threadsDoHelloFicamGuardadas() throws Exception {
        Distribuidor d = new Distribuidor(1);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        try (ServerSocket servidor = new ServerSocket(0)) {
            Par p = ligar(servidor, d, 0, erro);
            assertEquals(0, p.ligacao().threads(), "antes do HELLO ainda não se sabe");
            p.no().enviarHello(4, 3);
            long limite = System.nanoTime() + 2_000_000_000L;
            while (p.ligacao().threads() == 0 && System.nanoTime() < limite) Thread.sleep(1);
            assertEquals(3, p.ligacao().threads(), "as threads do nó, lidas no HELLO");
            p.no().close();
        }
    }

    @Test
    void repeticaoComecaComOcupadoAZero() {
        Repeticao rep = new Repeticao(GeradorDeTarefas.gerar(5, 1, "uniforme", 42), 6);
        assertEquals(6, rep.ocupadoPorLigacao.length(), "uma posição por ligação");
        for (int l = 0; l < 6; l++) assertEquals(0, rep.ocupadoPorLigacao.get(l));
    }
}
