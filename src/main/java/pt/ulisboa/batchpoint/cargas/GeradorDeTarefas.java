package pt.ulisboa.batchpoint.cargas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

public final class GeradorDeTarefas {

    private GeradorDeTarefas(){}

    public static Tarefa[] gerar(int n, long iteracoesMedias, String desequilibrio, long semente){

        Tarefa[] tarefas = new Tarefa[n];

        switch(desequilibrio){

            case "uniforme" -> {
                for(int i=0; i<n; i++){
                    tarefas[i]= new Tarefa(i, iteracoesMedias);
                }
            }

            case "agrupada" -> {
                //fracao das tarefas que serao longas
                int longas = n/ Config.TAREFA_FRACAO_LONGAS;

                long cargaCurta = Math.round(iteracoesMedias/ Config.TAREFA_DIVISOR_CURTA);
                long cargaLonga = cargaCurta * Config.TAREFA_FATOR_LONGA;

                for(int i=0; i<n; i++){
                    if(i<longas){
                        tarefas[i]= new Tarefa(i, cargaLonga);
                    }else{
                        tarefas[i]= new Tarefa(i, cargaCurta);
                    }
                }
            }

            case "baralhada" -> {

                List<Long> listaCargas = new ArrayList<>();

                int longas = n/ Config.TAREFA_FRACAO_LONGAS;

                long cargaCurta = Math.round(iteracoesMedias/ Config.TAREFA_DIVISOR_CURTA);
                long cargaLonga = cargaCurta * Config.TAREFA_FATOR_LONGA;

                for(int i=0; i<n; i++){
                    if(i<longas){
                        listaCargas.add(cargaLonga);
                    }else{
                        listaCargas.add(cargaCurta);
                    }
                }

                Collections.shuffle(listaCargas, new Random(semente));

                int i=0;
                for(long t : listaCargas){
                    tarefas[i] = new Tarefa(i, t);
                    i++;

                }
            }

            default -> throw new IllegalArgumentException("Desequilíbrio desconhecido: " + desequilibrio);
        
        }

        return tarefas;
    }


    public static Tarefa[] gerarMandelbrot(int lado){

        if(lado<=0 || Config.MANDEL_TAMANHO % lado != 0){
            throw new IllegalArgumentException("Lado invalido: " + lado + " (tem de ser positivo e dividir " + Config.MANDEL_TAMANHO + ")");
        }
        int blocosPorLado= (Config.MANDEL_TAMANHO / lado);
        int numBlocos = blocosPorLado*blocosPorLado;

        Tarefa[] tarefas = new Tarefa[numBlocos];

        for(int i=0; i<numBlocos;i++){

            tarefas[i] = new Tarefa(i, lado);

        }

        return tarefas;
    }

}
