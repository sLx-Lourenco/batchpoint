package pt.ulisboa.batchpoint.protocolo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Os tipos de mensagem têm de ser todos diferentes (fase 5). Gerado pelo Claude. */
class ProtocoloTest {

    @Test
    void tiposTodosDiferentes() {
        Set<Byte> tipos = new HashSet<>();
        tipos.add(Protocolo.HELLO);
        tipos.add(Protocolo.REQUEST);
        tipos.add(Protocolo.TASKS);
        tipos.add(Protocolo.RESULTS);
        tipos.add(Protocolo.DONE);
        assertEquals(5, tipos.size());
    }
}
