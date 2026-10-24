package pt.ulisboa.batchpoint.cargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Testes da fábrica de cargas (fase 3). Gerados pelo Claude. */
class FabricaCargasTest {

    @Test
    void criaASintetica() {
        assertTrue(FabricaCargas.criar("sintetica") instanceof Sintetica);
    }

    @Test
    void cargaCriadaPelaFabricaCalculaOMesmo() {
        assertEquals(new Sintetica().executar(9, 1_000), FabricaCargas.criar("sintetica").executar(9, 1_000));
    }

    @Test
    void nomeDesconhecidoDaErroComONome() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FabricaCargas.criar("sintetca"));
        assertTrue(e.getMessage().contains("sintetca"), e.getMessage());
    }

    @Test
    void nomeEmMaiusculasNaoEAceite() {
        assertThrows(IllegalArgumentException.class, () -> FabricaCargas.criar("Sintetica"));
    }
}
