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
 * Testes do registo em JSON (fases 7 e 11): um ficheiro por execução, na pasta da categoria,
 * com todas as repetições e sem temporários; na fase 11, o ocupado e o parado por ligação,
 * a eficiência e a temperatura. Escrevem numa pasta temporária, nunca em data/.
 * Gerados pelo Claude.
 */
class RegistoJsonTest {

    private static final Configuracao CONFIG = new Configuracao(Politica.PLANA, "sintetica", 1,
            "baralhada", 20_000, 5_972, 6, 2, 3);

    private static final long[] OCUPADO = {100, 200};
    private static final long[] PARADO = {10, 20};

    /** Escreve uma repetição com valores da fase 11 fixos (só os da fase 7 mudam). */
    private static void escrever(RegistoJson r, int rep, long tempo, long msgs, long bytes, long soma)
            throws Exception {
        r.escrever(rep, tempo, msgs, bytes, soma, OCUPADO, PARADO, 0.5, 51.25);
    }

    private static List<Path> ficheiros(Path pasta) throws Exception {
        try (Stream<Path> s = Files.list(pasta)) {
            return s.toList();
        }
    }

    @Test
    void umFicheiroComTodasAsRepeticoes() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson r = new RegistoJson(pasta, "piloto", "cabo", CONFIG, "--politica plana");
        escrever(r, 0, 788_000_000L, 60_000, 340_000, 203_000_000L);
        escrever(r, 1, 786_000_000L, 60_000, 340_000, 203_100_000L);

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
        escrever(r, 0, 1, 2, 3, 4);
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
        escrever(r, 0, 1, 2, 3, 4);
        assertTrue(Files.readString(r.ficheiro()).contains("\"comando\": \"--nome \\\"a b\\\"\""));
    }

    @Test
    void textoEscapaBarrasEAspas() {
        assertEquals("\"a\\\"b\\\\c\"", RegistoJson.texto("a\"b\\c"));
        assertEquals("\"\"", RegistoJson.texto(""));
    }

    @Test
    void camposDaFase11() throws Exception {
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson r = new RegistoJson(pasta, "testes", "", CONFIG, "x");
        r.escrever(0, 1_000, 2, 3, 4, new long[] {900, 950, 1_000}, new long[] {100, 50, 0}, 0.95, 52.3);
        String json = Files.readString(r.ficheiro());
        assertTrue(json.contains("\"eficiencia\": 0.95"), json);
        assertTrue(json.contains("\"temperatura_c\": 52.3"), json);
        assertTrue(json.contains("\"ocupado_ns\": [900, 950, 1000]"), json);
        assertTrue(json.contains("\"parado_ns\": [100, 50, 0]}"), json);
    }

    @Test
    void temperaturaNaoMedidaFicaNull() throws Exception {
        // Em localhost não há sensor: NaN não é JSON válido, por isso tem de sair null.
        Path pasta = Files.createTempDirectory("registo");
        RegistoJson r = new RegistoJson(pasta, "testes", "", CONFIG, "x");
        r.escrever(0, 1, 2, 3, 4, OCUPADO, PARADO, 0.5, Double.NaN);
        String json = Files.readString(r.ficheiro());
        assertTrue(json.contains("\"temperatura_c\": null"), json);
        assertTrue(!json.contains("NaN"), "NaN não é JSON válido");
    }

    @Test
    void listaENumeroEmJson() {
        assertEquals("[]", RegistoJson.lista(new long[0]));
        assertEquals("[7]", RegistoJson.lista(new long[] {7}));
        assertEquals("[1, -2, 3]", RegistoJson.lista(new long[] {1, -2, 3}));
        assertEquals("null", RegistoJson.numero(Double.NaN));
        assertTrue("0.5".equals(RegistoJson.numero(0.5)), "ponto decimal, nunca vírgula");
    }
}