package pt.ulisboa.batchpoint.registo;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Guarda uma execução do coordenador num ficheiro JSON próprio, em data/<categoria>/.
 *
 * O ficheiro tem a configuração, o ambiente e a lista das repetições medidas.
 * É reescrito no fim de cada repetição, primeiro para um ficheiro temporário e depois
 * com uma mudança de nome. No Linux a mudança de nome é atómica: o ficheiro final é sempre
 * a versão anterior completa ou a nova completa, nunca uma escrita a meio. Se a execução
 * falhar, ficam guardadas as repetições que já acabaram.
 *
 * Recebe só números (e não a Repeticao) para não depender do pacote coordenador.
 */
public final class RegistoJson {

    /** Categorias aceites; cada uma é uma pasta dentro da pasta base. */
    public static final Set<String> CATEGORIAS = Set.of("testes", "piloto", "final");

    private static final DateTimeFormatter NOME_FICHEIRO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final Path ficheiro;
    private final String inicio;                        // tudo o que vem antes da lista de repetições
    private final List<String> repeticoes = new ArrayList<>();

    /**
     * @param pasta     pasta base (normalmente "data")
     * @param categoria "testes", "piloto" ou "final"
     * @param nome      etiqueta acrescentada ao nome do ficheiro (pode ser vazia)
     * @param c         configuração da experiência
     * @param comando   os argumentos do coordenador, para se poder repetir a execução
     */
    public RegistoJson(Path pasta, String categoria, String nome, Configuracao c, String comando)
            throws IOException {
        if (!CATEGORIAS.contains(categoria)) {
            throw new IllegalArgumentException("Categoria desconhecida: " + categoria + " (aceites: " + CATEGORIAS + ")");
        }
        LocalDateTime agora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String nomeFicheiro = agora.format(NOME_FICHEIRO) + "_" + c.politica().tipo() + "_" + c.carga()
                + "_" + c.parametro() + "_" + c.desequilibrio() + "_lote" + c.lote()
                + (nome.isEmpty() ? "" : "_" + nome) + ".json";
        Path dir = pasta.resolve(categoria);
        Files.createDirectories(dir);
        this.ficheiro = dir.resolve(nomeFicheiro);

        this.inicio = "{\n"
                + "  \"data\": " + texto(agora.toString()) + ",\n"
                + "  \"comando\": " + texto(comando) + ",\n"
                + "  \"configuracao\": {\n"
                + "    \"politica\": " + texto(c.politica().tipo()) + ",\n"
                + "    \"carga\": " + texto(c.carga()) + ",\n"
                + "    \"lote\": " + c.lote() + ",\n"
                + "    \"desequilibrio\": " + texto(c.desequilibrio()) + ",\n"
                + "    \"tarefas\": " + c.tarefas() + ",\n"
                + "    \"parametro\": " + c.parametro() + ",\n"
                + "    \"ligacoes\": " + c.ligacoes() + ",\n"
                + "    \"aquecimento\": " + c.aquecimento() + ",\n"
                + "    \"repeticoes_pedidas\": " + c.repeticoes() + "\n"
                + "  },\n"
                + "  \"ambiente\": {\n"
                + "    \"coordenador\": " + texto(InetAddress.getLocalHost().getHostName()) + ",\n"
                + "    \"java\": " + texto(System.getProperty("java.version")) + ",\n"
                + "    \"sistema\": " + texto(System.getProperty("os.name") + " " + System.getProperty("os.version")) + "\n"
                + "  },\n";
    }

    /** Acrescenta uma repetição medida e regrava o ficheiro. */
        /** Acrescenta uma repetição medida e regrava o ficheiro. */
    public void escrever(int repeticao, long tempoNs, long mensagens, long bytesEnviados, long somaDuracoesNs,
            long[] ocupadoNs, long[] paradoNs, double eficiencia, double temperatura) throws IOException {
        repeticoes.add("    {\"repeticao\": " + repeticao
                + ", \"tempo_ns\": " + tempoNs
                + ", \"mensagens\": " + mensagens
                + ", \"bytes_enviados\": " + bytesEnviados
                + ", \"soma_duracoes_ns\": " + somaDuracoesNs
                + ", \"eficiencia\": " + numero(eficiencia)
                + ", \"temperatura_c\": " + numero(temperatura)
                + ", \"ocupado_ns\": " + lista(ocupadoNs)
                + ", \"parado_ns\": " + lista(paradoNs) + "}");
        gravar();
    }

    /** Um array de longs em JSON: [1, 2, 3]. */
    static String lista(long[] valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(valores[i]);
        }
        return sb.append("]").toString();
    }

    /** Um double em JSON; NaN (não medido) passa a null, porque o JSON não aceita NaN. */
    static String numero(double v) {
        return Double.isNaN(v) ? "null" : String.valueOf(v);
    }

    /** Onde o ficheiro está a ser gravado. */
    public Path ficheiro() {
        return ficheiro;
    }

    /** Escreve num temporário e muda-lhe o nome para o final (atómico). */
    private void gravar() throws IOException {
        String json = inicio
                + "  \"repeticoes\": [\n" + String.join(",\n", repeticoes) + "\n  ]\n"
                + "}\n";
        Path tmp = ficheiro.resolveSibling(ficheiro.getFileName() + ".tmp");
        Files.writeString(tmp, json);
        Files.move(tmp, ficheiro, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Um texto entre aspas, com as barras e as aspas escapadas (as barras primeiro). */
    static String texto(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
