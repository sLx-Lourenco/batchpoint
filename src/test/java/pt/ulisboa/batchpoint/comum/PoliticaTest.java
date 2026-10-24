package pt.ulisboa.batchpoint.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Testes do enum das políticas (fase 2). Gerados pelo Claude. */
class PoliticaTest {

    @Test
    void converteOsNomesDaLinhaDeComandos() {
        assertEquals(Politica.PLANA, Politica.parse("plana"));
        assertEquals(Politica.HIERARQUICA, Politica.parse("hierarquica"));
    }

    @Test
    void idaEVoltaParaTodasAsPoliticas() {
        // Continua a valer quando acrescentares o work stealing (fase 13).
        for (Politica p : Politica.values()) {
            assertEquals(p, Politica.parse(p.tipo()));
        }
    }

    @Test
    void tiposSaoDiferentesEmMinusculas() {
        Politica[] todas = Politica.values();
        for (int i = 0; i < todas.length; i++) {
            assertEquals(todas[i].tipo().toLowerCase(), todas[i].tipo());
            for (int j = i + 1; j < todas.length; j++) {
                assertTrue(!todas[i].tipo().equals(todas[j].tipo()), "dois valores com o mesmo tipo");
            }
        }
    }

    @Test
    void nomeDesconhecidoDaErroComONome() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Politica.parse("planna"));
        assertTrue(e.getMessage().contains("planna"), e.getMessage());
    }

    @Test
    void maiusculasNaoSaoAceites() {
        // Decisão atual: só se aceita exatamente o nome em minúsculas.
        assertThrows(IllegalArgumentException.class, () -> Politica.parse("PLANA"));
    }

    @Test
    void nullDaErro() {
        assertThrows(IllegalArgumentException.class, () -> Politica.parse(null));
    }
}
