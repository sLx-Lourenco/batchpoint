package pt.ulisboa.batchpoint.cargas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/** Testes do gerador de tarefas (fase 4). Gerados pelo Claude. */
class GeradorDeTarefasTest {

    private static final int N = 20_000;
    private static final long MEDIA = 5_971;     // 10 µs na torre
    private static final long SEMENTE = 42;

    private static Tarefa[] gerar(String desequilibrio) {
        return GeradorDeTarefas.gerar(N, MEDIA, desequilibrio, SEMENTE);
    }

    private static long total(Tarefa[] t) {
        long soma = 0;
        for (Tarefa x : t) {
            soma += x.parametro();
        }
        return soma;
    }

    private static long[] tamanhosOrdenados(Tarefa[] t) {
        long[] v = new long[t.length];
        for (int i = 0; i < t.length; i++) {
            v[i] = t[i].parametro();
        }
        Arrays.sort(v);
        return v;
    }

    private static long[] tamanhos(Tarefa[] t) {
        long[] v = new long[t.length];
        for (int i = 0; i < t.length; i++) {
            v[i] = t[i].parametro();
        }
        return v;
    }

    // ---------- comum às três ----------

    @Test
    void idsSaoAPosicaoESemBuracos() {
        for (String d : new String[] {"uniforme", "agrupada", "baralhada"}) {
            Tarefa[] t = gerar(d);
            assertEquals(N, t.length, d);
            for (int i = 0; i < N; i++) {
                assertTrue(t[i] != null, d + ": posição " + i + " ficou null");
                assertEquals(i, t[i].id(), d + ": id errado na posição " + i);
            }
        }
    }

    @Test
    void mesmoTrabalhoTotalNasTres() {
        double uniforme = total(gerar("uniforme"));
        for (String d : new String[] {"agrupada", "baralhada"}) {
            double diferenca = Math.abs(total(gerar(d)) - uniforme) / uniforme;
            assertTrue(diferenca < 0.001, d + ": diferença de " + (diferenca * 100) + "% no trabalho total");
        }
    }

    @Test
    void zeroTarefasDaArrayVazio() {
        for (String d : new String[] {"uniforme", "agrupada", "baralhada"}) {
            assertEquals(0, GeradorDeTarefas.gerar(0, MEDIA, d, SEMENTE).length, d);
        }
    }

    @Test
    void desequilibrioDesconhecidoDaErro() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeradorDeTarefas.gerar(10, MEDIA, "misturada", SEMENTE));
        assertTrue(e.getMessage().contains("misturada"), e.getMessage());
    }

    // ---------- uniforme ----------

    @Test
    void uniformeTemTodasAMedia() {
        for (Tarefa x : gerar("uniforme")) {
            assertEquals(MEDIA, x.parametro());
        }
    }

    // ---------- agrupada ----------

    @Test
    void agrupadaTemAsLongasTodasNoInicio() {
        Tarefa[] t = gerar("agrupada");
        int longas = N / Config.TAREFA_FRACAO_LONGAS;
        long longa = t[0].parametro();
        long curta = t[N - 1].parametro();
        for (int i = 0; i < N; i++) {
            assertEquals(i < longas ? longa : curta, t[i].parametro(), "posição " + i);
        }
    }

    @Test
    void agrupadaLongaVale10Curtas() {
        Tarefa[] t = gerar("agrupada");
        assertEquals(t[N - 1].parametro() * Config.TAREFA_FATOR_LONGA, t[0].parametro());
    }

    @Test
    void agrupadaTemAMediaPedida() {
        double media = (double) total(gerar("agrupada")) / N;
        assertTrue(Math.abs(media - MEDIA) / MEDIA < 0.001, "média " + media + " em vez de " + MEDIA);
    }

    // ---------- baralhada ----------

    @Test
    void baralhadaTemAsMesmasTarefasDaAgrupada() {
        assertArrayEquals(tamanhosOrdenados(gerar("agrupada")), tamanhosOrdenados(gerar("baralhada")));
    }

    @Test
    void baralhadaEstaMesmoBaralhada() {
        Tarefa[] t = gerar("baralhada");
        long longa = tamanhosOrdenados(t)[N - 1];
        int longas = N / Config.TAREFA_FRACAO_LONGAS;
        int longasNoInicio = 0;
        for (int i = 0; i < longas; i++) {
            if (t[i].parametro() == longa) {
                longasNoInicio++;
            }
        }
        // Ao acaso, espera-se ~10% das longas nas primeiras posições; nunca todas.
        assertTrue(longasNoInicio < longas / 2, longasNoInicio + " das " + longas + " longas no início");
    }

    @Test
    void mesmaSementeDaAMesmaOrdem() {
        assertArrayEquals(tamanhos(gerar("baralhada")), tamanhos(gerar("baralhada")));
    }

    @Test
    void sementeDiferenteDaOrdemDiferente() {
        long[] a = tamanhos(GeradorDeTarefas.gerar(N, MEDIA, "baralhada", 1));
        long[] b = tamanhos(GeradorDeTarefas.gerar(N, MEDIA, "baralhada", 2));
        assertTrue(!Arrays.equals(a, b), "sementes diferentes deram a mesma ordem");
    }
}
