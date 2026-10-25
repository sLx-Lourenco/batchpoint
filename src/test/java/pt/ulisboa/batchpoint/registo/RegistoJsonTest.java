package pt.ulisboa.batchpoint.registo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.comum.Politica;

/**
 * Testes do registo em JSON (fase 7): um ficheiro por execução, na pasta da categoria,
 * com todas as repetições e sem temporários. Escrevem numa pasta temporária, nunca em data/.
 * Gerados pelo Claude.
 */
class RegistoJsonTest {

    private static final Configuracao CONFIG = new Configuracao(Politica.PLANA, "sintetica", 1,
            "baralhada", 20_000, 5_972, 6, 2, 3);

    private static List<Path> ficheiros(Path pasta) throws Exception {
        try (Stream<Path> s = Files.list(pasta)) {
            return s.toList();
        }
    }

    @Test
    void umFicheiroComTodasAsRepeticoes() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson r = new RegistoJson(pasta, "piloto", "cabo", CONFIG, "--politica plana");
        r.escrever(0, 788_000_000L, 60_000, 340_000, 203_000_000L);
        r.escrever(1, 786_000_000L, 60_000, 340_000, 203_100_000L);

        List<Path> fs = ficheiros(pasta.resolve("piloto"));
        assertEquals(1, fs.size(), "um só ficheiro, sem .tmp");
        assertTrue(fs.get(0).equals(r.ficheiro()), "ficheiro() devolve o caminho certo");

        String json = Files.readString(r.ficheiro());
        assertTrue(json.contains("\"politica\": \"plana\""), "política");
        assertTrue(json.contains("\"tarefas\": 20000"), "tarefas");
        assertTrue(json.contains("\"repeticoes_pedidas\": 3"), "repetições pedidas");
        assertTrue(json.contains("{\"repeticao\": 0, \"tempo_ns\": 788000000, \"mensagens\": 60000"), "repetição 0");
        assertTrue(json.contains("{\"repeticao\": 1, \"tempo_ns\": 786000000"), "repetição 1");
        assertTrue(json.startsWith("{\n") && json.endsWith("  ]\n}\n"), "ficheiro fechado");
    }

    @Test
    void ficheiroValeMesmoComUmaSoRepeticao() throws Exception {
        // Simula uma execução interrompida depois da primeira repetição.
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson r = new RegistoJson(pasta, "testes", "", CONFIG, "x");
        r.escrever(0, 1, 2, 3, 4);
        String json = Files.readString(r.ficheiro());
        assertTrue(json.contains("\"repeticoes\": [\n    {\"repeticao\": 0"), json);
        assertTrue(json.endsWith("}\n  ]\n}\n"), "sem vírgula a mais depois da última repetição");
    }

    @Test
    void nomeDoFicheiroTemAConfiguracao() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson comNome = new RegistoJson(pasta, "testes", "cabo", CONFIG, "x");
        assertTrue(comNome.ficheiro().getFileName().toString()
                .endsWith("_plana_sintetica_5972_baralhada_lote1_cabo.json"), comNome.ficheiro().toString());

        RegistoJson semNome = new RegistoJson(pasta, "testes", "", CONFIG, "x");
        assertTrue(semNome.ficheiro().getFileName().toString()
                .endsWith("_plana_sintetica_5972_baralhada_lote1.json"), semNome.ficheiro().toString());
    }

    @Test
    void criaAPastaDaCategoria() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        new RegistoJson(pasta, "final", "", CONFIG, "x");
        assertTrue(Files.isDirectory(pasta.resolve("final")));
    }

    @Test
    void categoriaDesconhecidaDaErro() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new RegistoJson(pasta, "finl", "", CONFIG, "x"));
        assertTrue(e.getMessage().contains("finl"), e.getMessage());
    }

    @Test
    void comandoComAspasFicaEscapado() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson r = new RegistoJson(pasta, "testes", "", CONFIG, "--nome \"a b\"");
        r.escrever(0, 1, 2, 3, 4);
        assertTrue(Files.readString(r.ficheiro()).contains("\"comando\": \"--nome \\\"a b\\\"\""));
    }

    @Test
    void textoEscapaBarrasEAspas() {
        assertEquals("\"a\\\"b\\\\c\"", RegistoJson.texto("a\"b\\c"));
        assertEquals("\"\"", RegistoJson.texto(""));
    }
}
