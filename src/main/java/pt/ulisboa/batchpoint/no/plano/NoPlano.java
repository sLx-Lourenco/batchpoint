package pt.ulisboa.batchpoint.no.plano;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.batchpoint.cargas.Carga;
import pt.ulisboa.batchpoint.no.AgenteNo;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;


public final class NoPlano {
    
    private NoPlano(){}

    public static void correr(InetSocketAddress coordenador, int id, int threads, Carga carga) throws InterruptedException{

        List<Thread> listaThreads = new ArrayList<Thread>(); 

        for(int i=0; i<threads; i++){
            Thread t = new Thread(()->{
                work(coordenador, id, threads, carga);
            });

            t.start();
            listaThreads.add(t);
        }

        for(Thread t : listaThreads){
            t.join();
        }



    }

    static void work(InetSocketAddress coordenador, int id, int threads, Carga carga){

        try(Canal canal = new Canal(AgenteNo.ligar(coordenador));){
            
            canal.enviarHello(id, threads);

            while(true){
                canal.enviarPedido(1);
                byte tipo = canal.lerTipo();

                if(tipo == Protocolo.DONE){
                    return;
                }

                if(tipo != Protocolo.TASKS){
                    throw new IllegalStateException("Estava a espera de tasks(3) veio " + tipo);
                }

                List<Tarefa> tarefas = canal.lerTarefas();
                List<Resultado> resultados = new ArrayList<>();

                for(Tarefa t : tarefas){
                    long inicio = System.nanoTime();
                    long valor = carga.executar(t.id(), t.parametro());
                    long fim = System.nanoTime()-inicio;

                    resultados.add(new Resultado(t.id(), valor, fim));
                }

                canal.enviarResultados(resultados);
            }




        }catch(IOException | InterruptedException e){ throw new RuntimeException(e);}
    }


}
