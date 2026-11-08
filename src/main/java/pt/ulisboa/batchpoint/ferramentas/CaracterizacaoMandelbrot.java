package pt.ulisboa.batchpoint.ferramentas;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.cargas.Mandelbrot;
import pt.ulisboa.batchpoint.comum.Argumentos;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

/**
 * Mede o custo de cada bloco do Mandelbrot num só core, para cada lado da grelha.
 *
 * Uso:   java -cp batchpoint.jar pt.ulisboa.batchpoint.ferramentas.CaracterizacaoMandelbrot [--pasta P]
 *
 * Imprime uma tabela por lado (soma, total, média, mediana, p99, máximo) e grava em
 * P/mandelbrot_blocos.csv (por omissão data/caracterizacao/) uma linha por bloco:
 *   lado, id, linha, coluna, iteracoes, duracao_ns
 * As iterações são o valor devolvido pela carga (o custo exato do bloco); a duração é o
 * tempo medido só à volta do executar.
 */
public final class CaracterizacaoMandelbrot {

    private CaracterizacaoMandelbrot() {
    }

    public static void main(String[] args) throws IOException {
        Argumentos a = new Argumentos(args);
        Path pasta = Path.of(a.texto(Config.ARG_PASTA, Config.CARACT_PASTA_POR_OMISSAO));
        Files.createDirectories(pasta);
        Path ficheiro = pasta.resolve(Config.CARACT_FICHEIRO_BLOCOS);

        Mandelbrot m = new Mandelbrot();
        long acumulado = 0;

        // aquecimento: a imagem inteira uma vez, sem medir
        for (Tarefa t : GeradorDeTarefas.gerarMandelbrot(Config.MANDEL_LADO_AQUECIMENTO)) {
            acumulado ^= m.executar(t.id(), t.parametro());
        }

        System.out.printf("%6s %8s %14s %10s %10s %10s %10s %10s%n",
                "lado", "tarefas", "soma", "total ms", "media ms", "mediana", "p99 ms", "max ms");

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(ficheiro))) {
            csv.println("lado,id,linha,coluna,iteracoes,duracao_ns");

            for (int lado : Config.MANDEL_LADOS) {
                Tarefa[] tarefas = GeradorDeTarefas.gerarMandelbrot(lado);
                int blocosPorLinha = Config.MANDEL_TAMANHO / lado;
                long[] valores = new long[tarefas.length];
                long[] duracoes = new long[tarefas.length];
                long soma = 0;

                // Medição: só o executar entre os dois nanoTime; os resultados ficam em arrays.
                for (int i = 0; i < tarefas.length; i++) {
                    Tarefa t = tarefas[i];
                    long inicio = System.nanoTime();
                    valores[i] = m.executar(t.id(), t.parametro());
                    duracoes[i] = System.nanoTime() - inicio;
                    soma += valores[i];
                }

                // O CSV só se escreve depois de medir o lado inteiro: formatar texto e escrever
                // no disco entre medições perturbava a cache e o GC (+1,5 % nas durações).
                for (int i = 0; i < tarefas.length; i++) {
                    int id = tarefas[i].id();
                    csv.printf("%d,%d,%d,%d,%d,%d%n", lado, id,
                            id / blocosPorLinha, id % blocosPorLinha, valores[i], duracoes[i]);
                }

                // As estatísticas usam uma cópia ordenada: o CSV fica pela ordem dos blocos.
                long[] ordenadas = duracoes.clone();
                Arrays.sort(ordenadas);
                long total = 0;
                for (long d : ordenadas) {
                    total += d;
                }
                double media = (double) total / ordenadas.length;
                long mediana = ordenadas[ordenadas.length / 2];
                long p99 = ordenadas[(int) (ordenadas.length * 0.99)];
                long maximo = ordenadas[ordenadas.length - 1];

                System.out.printf("%6d %8d %14d %10.1f %10.3f %10.3f %10.3f %10.1f%n",
                        lado, tarefas.length, soma, ms(total), ms(media), ms(mediana), ms(p99), ms(maximo));
                acumulado ^= soma;
            }
        }

        System.out.println("Blocos gravados em " + ficheiro);
        System.out.println("(ignorar) " + acumulado);
    }

    private static double ms(double ns) {
        return ns / 1_000_000.0;
    }
}
