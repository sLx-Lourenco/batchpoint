package pt.ulisboa.batchpoint.no;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;

/**
 * Testes do AgenteNo (fases 6 e 9): o ligar com novas tentativas e o main a escolher a
 * política e a passar os argumentos (sobretudo o --lote). O teste faz de coordenador
 * num socket local, com tempo limite de leitura. Gerados pelo Claude.
 */
class AgenteNoTest {

    private static Canal aceitar(ServerSocket servidor) throws IOException {
        Socket s = servidor.accept();
        s.setSoTimeout(5_000);
        return new Canal(s);
    }

    /** Corre o main numa thread, guardando a exceção (se houver). */
    private static Thread correrMain(AtomicReference<Throwable> erro, String... args) {
        Thread t = new Thread(() -> {
            try {
                AgenteNo.main(args);
            } catch (Throwable e) {
                erro.set(e);
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Test
    void ligarEsperaQueOCoordenadorAbra() throws Exception {
        int porta;
        try (ServerSocket livre = new ServerSocket(0)) {
            porta = livre.getLocalPort();               // uma porta livre, fechada logo a seguir
        }
        int portaFinal = porta;
        AtomicReference<ServerSocket> servidor = new AtomicReference<>();
        Thread abre = new Thread(() -> {
            try {
                Thread.sleep(3 * Config.NO_INTERVALO_TENTATIVAS_MS);   // abre só depois de 2 ou 3 tentativas falhadas
                servidor.set(new ServerSocket(portaFinal));
                servidor.get().accept().close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        abre.start();
        try (Socket s = AgenteNo.ligar(InetSocketAddress.createUnresolved("localhost", portaFinal))) {
            assertTrue(s.isConnected(), "o ligar devia ter insistido até o servidor abrir");
        }
        abre.join(5_000);
        servidor.get().close();
    }

    @Test
    void mainHierarquicaPedeOLoteDosArgumentos() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            AtomicReference<Throwable> erro = new AtomicReference<>();
            Thread no = correrMain(erro, "--modo", "hierarquica", "--id", "4", "--threads", "2",
                    "--lote", "7", "--coordenador", "localhost:" + servidor.getLocalPort());
            try (Canal c = aceitar(servidor)) {
                assertEquals(Protocolo.HELLO, c.lerTipo());
                assertEquals(4, c.lerInt(), "id");
                assertEquals(2, c.lerInt(), "threads");
                assertEquals(Protocolo.REQUEST, c.lerTipo());
                assertEquals(7, c.lerInt(), "o nó hierárquico pede o --lote");
                c.enviarDone();
                no.join(5_000);
                assertTrue(!no.isAlive(), "o main devia terminar depois do DONE");
                assertTrue(erro.get() == null, "erro no main: " + erro.get());
            }
        }
    }

    @Test
    void mainHierarquicaSemLoteUsaOmissao() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            AtomicReference<Throwable> erro = new AtomicReference<>();
            Thread no = correrMain(erro, "--modo", "hierarquica", "--id", "1",
                    "--coordenador", "localhost:" + servidor.getLocalPort());
            try (Canal c = aceitar(servidor)) {
                c.lerTipo();
                c.lerInt();
                assertEquals(Config.THREADS_POR_OMISSAO, c.lerInt(), "threads por omissão");
                c.lerTipo();
                assertEquals(Config.COORD_LOTE_POR_OMISSAO, c.lerInt(), "lote por omissão");
                c.enviarDone();
                no.join(5_000);
                assertTrue(!no.isAlive());
            }
        }
    }

    @Test
    void mainPlanaAbreUmaLigacaoPorThread() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0)) {
            AtomicReference<Throwable> erro = new AtomicReference<>();
            Thread no = correrMain(erro, "--modo", "plana", "--id", "2", "--threads", "3",
                    "--coordenador", "localhost:" + servidor.getLocalPort());
            List<Canal> canais = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Canal c = aceitar(servidor);
                canais.add(c);
                assertEquals(Protocolo.HELLO, c.lerTipo());
                assertEquals(2, c.lerInt());
                assertEquals(3, c.lerInt());
            }
            for (Canal c : canais) {
                assertEquals(Protocolo.REQUEST, c.lerTipo());
                assertEquals(1, c.lerInt(), "na plana pede-se 1, mesmo com --lote");
                c.enviarDone();
            }
            no.join(5_000);
            assertTrue(!no.isAlive(), "o main devia terminar depois dos DONE");
            assertTrue(erro.get() == null, "erro no main: " + erro.get());
            for (Canal c : canais) c.close();
        }
    }

    @Test
    void modoInvalidoDaErro() {
        assertThrows(IllegalArgumentException.class,
                () -> AgenteNo.main(new String[] {"--modo", "estrela", "--id", "1", "--coordenador", "localhost:9"}));
    }

    @Test
    void faltaOIdDaErro() {
        assertThrows(IllegalArgumentException.class,
                () -> AgenteNo.main(new String[] {"--modo", "plana", "--coordenador", "localhost:9"}));
    }
}
