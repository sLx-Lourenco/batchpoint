package pt.ulisboa.batchpoint.ferramentas;

import java.util.Arrays;

import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.cargas.Mandelbrot;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

public final class CaracterizacaoMandelbrot {

    private CaracterizacaoMandelbrot() {
    }

    public static void main(String[] args) {
        Mandelbrot m = new Mandelbrot();
        long acumulado = 0;


        Tarefa[] tarefasAquec = GeradorDeTarefas.gerarMandelbrot(Config.MANDEL_LADO_AQUECIMENTO);

        //aquecimento
        for(Tarefa t : tarefasAquec){
            acumulado^=m.executar(t.id(), t.parametro());
        }

        System.out.printf("%6s %8s %14s %10s %10s %10s %10s %10s%n",
                "lado", "tarefas", "soma", "total ms", "media ms", "mediana", "p99 ms", "max ms");

        for (int lado : Config.MANDEL_LADOS) {
            Tarefa[] tarefas = GeradorDeTarefas.gerarMandelbrot(lado);
            long[] duracoes = new long[tarefas.length];
            long soma = 0;
            int i=0;
            long valor;

            for(Tarefa t : tarefas){

                long inicioMed = System.nanoTime();
                valor= m.executar(t.id(), t.parametro());//truque para medir so o calculo
                long rMedicao = (System.nanoTime() - inicioMed);

                duracoes[i]=rMedicao;
                soma+=valor;

                i++;
            }
            

            Arrays.sort(duracoes);


            long total = 0;
            for(long d : duracoes){
                total+=d;
            }

            double media = (double) total / duracoes.length;
            long mediana = duracoes[duracoes.length / 2];
            long p99 = duracoes[(int) (duracoes.length * 0.99)];
            long maximo = duracoes[duracoes.length - 1];

            System.out.printf("%6d %8d %14d %10.1f %10.3f %10.3f %10.3f %10.1f%n",
                    lado, tarefas.length, soma, ms(total), ms(media), ms(mediana), ms(p99), ms(maximo));
            acumulado ^= soma;
        }

        System.out.println("(ignorar) " + acumulado);
    }

    private static double ms(double ns) {
        return ns / 1_000_000.0;
    }
}