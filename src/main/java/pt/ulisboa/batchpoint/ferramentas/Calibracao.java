package pt.ulisboa.batchpoint.ferramentas;

import java.util.Arrays;

import pt.ulisboa.batchpoint.cargas.Sintetica;
import pt.ulisboa.batchpoint.comum.Config;

public class Calibracao {

    private Calibracao() {
    }

    public static void main(String[] args) {
        int nRepeticoes = Config.CALIBRACAO_REPETICOES;

        long iteracAquec = Config.CALIBRACAO_ITERACOES_AQUECIMENTO;
        long inicio = System.nanoTime();

        Sintetica s = new Sintetica();
        int x = 0;

        long acumulado = 0;

        //aquecimento
        while (System.nanoTime() - inicio < Config.CALIBRACAO_AQUECIMENTO_NS) {
            acumulado ^= s.executar(x, iteracAquec);
            x++;
        }

        double[] nsPorIteracao = new double[nRepeticoes];

        long iteracoes = Config.CALIBRACAO_ITERACOES_MEDICAO;

        //medicao
        for (int i = 0; i < nRepeticoes; i++) {

            long inicioMed = System.nanoTime();
            acumulado ^= s.executar(i, iteracoes);
            double rMedicao = (double) (System.nanoTime() - inicioMed) / iteracoes;

            nsPorIteracao[i] = rMedicao;
        }

        Arrays.sort(nsPorIteracao);
        double mediana = nsPorIteracao[nRepeticoes / 2];

        long[] alvo = Config.CALIBRACAO_ALVOS_NS;

        long[] iteracoesPorAlvo = new long[alvo.length];
        for (int i = 0; i < alvo.length; i++) {
            iteracoesPorAlvo[i] = Math.round(alvo[i] / mediana);

        }

        //para cada alvo, corre tarefas seguidas durante 1s e mede a media

        System.out.printf("ns por iteracao (mediana de %d): %.4f%n%n", nRepeticoes, mediana);
        System.out.printf("%12s %12s %16s%n", "alvo (ns)", "iteracoes", "medido (ns)");

        int id = 0;
        for (int i = 0; i < alvo.length; i++) {

            long tarefas = Math.max(1, Config.CALIBRACAO_CONFIRMACAO_NS / alvo[i]);
            long t0 = System.nanoTime();

            for (long t = 0; t < tarefas; t++) {
                acumulado ^= s.executar(id++, iteracoesPorAlvo[i]);
            }

            double medido = (double) (System.nanoTime() - t0) / tarefas;
            System.out.printf("%12d %12d %16.1f%n", alvo[i], iteracoesPorAlvo[i], medido);
        }

        System.out.println("(ignorar) " + acumulado);

    }
}
