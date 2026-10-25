package pt.ulisboa.batchpoint.protocolo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes do canal (fase 5): cada mensagem faz a ida e volta por um socket local
 * e chega igual, e a contagem de bytes bate com os tamanhos do protocolo.
 * Gerados pelo Claude.
 */
class CanalTest {

    private ServerSocket servidor;
    private Socket socketNo;
    private Canal no;            // o lado que liga (como um nó)
    private Canal coordenador;   // o lado que aceita (como o coordenador)

    @BeforeEach
    void ligar() throws IOException {
        servidor = new ServerSocket(0);                      // 0 = uma porta livre qualquer
        socketNo = new Socket("localhost", servidor.getLocalPort());
        no = new Canal(socketNo);
        coordenador = new Canal(servidor.accept());
    }

    @AfterEach
    void desligar() throws IOException {
        no.close();
        coordenador.close();
        servidor.close();
    }

    // ---------- cada mensagem chega igual ----------

    @Test
    void hello() throws IOException {
        no.enviarHello(3, 2);
        assertEquals(Protocolo.HELLO, coordenador.lerTipo());
        assertEquals(3, coordenador.lerInt());
        assertEquals(2, coordenador.lerInt());
    }

    @Test
    void pedido() throws IOException {
        no.enviarPedido(100);
        assertEquals(Protocolo.REQUEST, coordenador.lerTipo());
        assertEquals(100, coordenador.lerInt());
    }

    @Test
    void tarefas() throws IOException {
        List<Tarefa> enviadas = List.of(new Tarefa(0, 5_971), new Tarefa(1, Long.MAX_VALUE), new Tarefa(19_999, -1));
        coordenador.enviarTarefas(enviadas);
        assertEquals(Protocolo.TASKS, no.lerTipo());
        assertEquals(enviadas, no.lerTarefas());
    }

    @Test
    void listaDeTarefasVazia() throws IOException {
        coordenador.enviarTarefas(List.of());
        assertEquals(Protocolo.TASKS, no.lerTipo());
        assertTrue(no.lerTarefas().isEmpty());
    }

    @Test
    void resultados() throws IOException {
        List<Resultado> enviados = List.of(new Resultado(7, 0x8000_0000_0000_0001L, 10_083), new Resultado(8, -42, 0));
        no.enviarResultados(enviados);
        assertEquals(Protocolo.RESULTS, coordenador.lerTipo());
        assertEquals(enviados, coordenador.lerResultados());
    }

    @Test
    void done() throws IOException {
        coordenador.enviarDone();
        assertEquals(Protocolo.DONE, no.lerTipo());
    }

    @Test
    void loteGrandeChegaInteiro() throws IOException {
        List<Tarefa> enviadas = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            enviadas.add(new Tarefa(i, 3_143L * i));
        }
        coordenador.enviarTarefas(enviadas);
        assertEquals(Protocolo.TASKS, no.lerTipo());
        assertEquals(enviadas, no.lerTarefas());
    }

    @Test
    void conversaCompletaPelaOrdem() throws IOException {
        // Uma troca como a da plana: HELLO, REQUEST, TASKS, RESULTS, REQUEST, DONE.
        no.enviarHello(1, 2);
        no.enviarPedido(1);
        assertEquals(Protocolo.HELLO, coordenador.lerTipo());
        assertEquals(1, coordenador.lerInt());
        assertEquals(2, coordenador.lerInt());
        assertEquals(Protocolo.REQUEST, coordenador.lerTipo());
        assertEquals(1, coordenador.lerInt());

        coordenador.enviarTarefas(List.of(new Tarefa(4, 10)));
        assertEquals(Protocolo.TASKS, no.lerTipo());
        assertEquals(List.of(new Tarefa(4, 10)), no.lerTarefas());

        no.enviarResultados(List.of(new Resultado(4, 99, 123)));
        no.enviarPedido(1);
        assertEquals(Protocolo.RESULTS, coordenador.lerTipo());
        assertEquals(List.of(new Resultado(4, 99, 123)), coordenador.lerResultados());
        assertEquals(Protocolo.REQUEST, coordenador.lerTipo());
        assertEquals(1, coordenador.lerInt());

        coordenador.enviarDone();
        assertEquals(Protocolo.DONE, no.lerTipo());
    }

    // ---------- bytes enviados ----------

    @Test
    void bytesDeCadaMensagem() throws IOException {
        assertEquals(0, no.bytesEnviados());
        no.enviarHello(1, 2);
        assertEquals(9, no.bytesEnviados(), "HELLO = 1 + 4 + 4");
        no.enviarPedido(1);
        assertEquals(9 + 5, no.bytesEnviados(), "REQUEST = 1 + 4");
        no.enviarResultados(List.of(new Resultado(1, 2, 3), new Resultado(4, 5, 6)));
        assertEquals(9 + 5 + (5 + 2 * 20), no.bytesEnviados(), "RESULTS = 5 + 20n");

        coordenador.enviarTarefas(List.of(new Tarefa(1, 2), new Tarefa(3, 4), new Tarefa(5, 6)));
        assertEquals(5 + 3 * 12, coordenador.bytesEnviados(), "TASKS = 5 + 12n");
        coordenador.enviarDone();
        assertEquals(5 + 3 * 12 + 1, coordenador.bytesEnviados(), "DONE = 1");
    }

    // ---------- configuração ----------

    @Test
    void nagleDesligado() throws IOException {
        assertTrue(socketNo.getTcpNoDelay(), "o Canal deve ligar o TCP_NODELAY");
    }
}
