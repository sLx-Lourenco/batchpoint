package pt.ulisboa.batchpoint.comum;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Testes da leitura de argumentos "--nome valor" (fase 2). Gerados pelo Claude. */
class ArgumentosTest {

    private static Argumentos ler(String... args) {
        return new Argumentos(args);
    }

    // ---------- leitura normal ----------

    @Test
    void leTextoInteiroELongo() {
        Argumentos a = ler("--politica", "plana", "--lote", "20", "--iteracoes", "5972");
        assertEquals("plana", a.texto("politica"));
        assertEquals(20, a.inteiro("lote", 1));
        assertEquals(20, a.inteiro("lote"));
        assertEquals(5972L, a.longo("iteracoes"));
    }

    @Test
    void longoAceitaValoresMaioresQueUmInt() {
        Argumentos a = ler("--iteracoes", "59715298000");
        assertEquals(59_715_298_000L, a.longo("iteracoes"));
    }

    @Test
    void semArgumentosUsaAsOmissoes() {
        Argumentos a = ler();
        assertEquals("plana", a.texto("modo", "plana"));
        assertEquals(Config.LOTE_POR_OMISSAO, a.inteiro("lote", Config.LOTE_POR_OMISSAO));
    }

    @Test
    void omissaoSoQuandoNaoVem() {
        Argumentos a = ler("--lote", "7");
        assertEquals(7, a.inteiro("lote", 1));
        assertEquals(2, a.inteiro("threads", 2));
        assertEquals("x", a.texto("carga", "x"));
    }

    @Test
    void argumentoRepetidoDaErro() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ler("--lote", "10", "--lote", "20"));
        assertTrue(e.getMessage().contains("--lote"), e.getMessage());
    }

    // ---------- erros no construtor ----------

    @Test
    void nomeSemTracosEInvalido() {
        assertThrows(IllegalArgumentException.class, () -> ler("lote", "20"));
    }

    @Test
    void nomeSemValorNoFim() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ler("--politica", "plana", "--lote"));
        assertTrue(e.getMessage().contains("--lote"), "a mensagem deve dizer qual argumento: " + e.getMessage());
    }

    // ---------- obrigatórios em falta ----------

    @Test
    void textoObrigatorioEmFaltaDizQualFalta() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ler("--lote", "5").texto("politica"));
        assertTrue(e.getMessage().contains("--politica"), e.getMessage());
    }

    @Test
    void inteiroObrigatorioEmFaltaDizQueFalta() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ler().inteiro("id"));
        assertTrue(e.getMessage().contains("Falta"), "deve dizer que falta, não que é inválido: " + e.getMessage());
    }

    @Test
    void longoObrigatorioEmFalta() {
        assertThrows(IllegalArgumentException.class, () -> ler().longo("iteracoes"));
    }

    // ---------- números inválidos ----------

    @Test
    void inteiroInvalidoDizQualArgumento() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ler("--lote", "abc").inteiro("lote", 1));
        assertTrue(e.getMessage().contains("--lote"), e.getMessage());
    }

    @Test
    void inteiroObrigatorioInvalido() {
        assertThrows(IllegalArgumentException.class, () -> ler("--id", "dois").inteiro("id"));
    }

    @Test
    void longoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> ler("--iteracoes", "1e6").longo("iteracoes"));
    }

    // ---------- endereço ip:porta ----------

    @Test
    void enderecoValido() {
        InetSocketAddress e = ler("--coordenador", "10.42.0.1:9000").endereco("coordenador");
        assertEquals("10.42.0.1", e.getHostString());
        assertEquals(9000, e.getPort());
    }

    @Test
    void enderecoComNomeDeMaquina() {
        InetSocketAddress e = ler("--coordenador", "localhost:9100").endereco("coordenador");
        assertEquals("localhost", e.getHostString());
        assertEquals(9100, e.getPort());
    }

    @Test
    void enderecoSemPorta() {
        assertThrows(IllegalArgumentException.class, () -> ler("--coordenador", "10.42.0.1").endereco("coordenador"));
        assertThrows(IllegalArgumentException.class, () -> ler("--coordenador", "10.42.0.1:").endereco("coordenador"));
    }

    @Test
    void enderecoSemHost() {
        assertThrows(IllegalArgumentException.class, () -> ler("--coordenador", ":9000").endereco("coordenador"));
    }

    @Test
    void enderecoComPortaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> ler("--coordenador", "host:abc").endereco("coordenador"));
        assertThrows(IllegalArgumentException.class, () -> ler("--coordenador", "host:0").endereco("coordenador"));
        assertThrows(IllegalArgumentException.class, () -> ler("--coordenador", "host:70000").endereco("coordenador"));
    }

    @Test
    void enderecoObrigatorioEmFalta() {
        assertThrows(IllegalArgumentException.class, () -> ler().endereco("coordenador"));
    }
}
