package pt.ulisboa.batchpoint.coordenador;

import java.io.IOException;
import java.util.List;

import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;

public final class Ligacao implements  Runnable{

    private final Canal canal;
    private final Distribuidor distribuidor;
    private final int indice;          // posicao desta ligacao no ocupadoPorLigacao
    private volatile int threads;      // threads do no lidas no HELLO

    Ligacao(Canal canal, Distribuidor distribuidor, int indice){
        this.canal = canal;
        this.distribuidor=distribuidor;
        this.indice=indice;
    }

    @Override 
    public void run(){

        try(canal){
            //HELLO
            byte tipo = canal.lerTipo();

            if(tipo!= Protocolo.HELLO){
                throw new IllegalStateException("Esperva HELLO(1) veio: " + tipo);
            }

            int id = canal.lerInt();
            threads = canal.lerInt();

            System.out.println("No " + id + " ligado (" + threads +" threads)");

            //Ciclo de msg
            Repeticao repeticao = null;

            while(true){
                tipo = canal.lerTipo();

                switch (tipo) {
                    case Protocolo.REQUEST -> {
                        int pedido= canal.lerInt();
                        Entrega entrega = distribuidor.obterTarefas(pedido);

                        if(entrega == null){
                            canal.enviarDone();
                            return;
                        }

                        long nBytes = canal.bytesEnviados();
                        canal.enviarTarefas(entrega.listaTarefas());
                        repeticao = entrega.rep();

                        long difBytes = canal.bytesEnviados() -nBytes;
                        repeticao.bytesEnviados.addAndGet(difBytes);
                        repeticao.mensagens.addAndGet(2);
                    }


                    case Protocolo.RESULTS -> {
                        List<Resultado> resultados = canal.lerResultados();
                        repeticao.mensagens.incrementAndGet();

                        for(Resultado r : resultados){
                            repeticao.recebidos.incrementAndGet(r.id());
                            //podia usar soma mas vou usar XOR so pk e habitual em mpressoes digitais
                            repeticao.checkSum.accumulateAndGet(r.valor(), (a, b) -> a ^ b);
                            repeticao.somaDuracaoNs.addAndGet(r.duracaoNs());
                            repeticao.ocupadoPorLigacao.addAndGet(indice, r.duracaoNs());// metricas
                            repeticao.cDownLatch.countDown();
                        }
                        
                    }
                
                    default -> throw new IllegalStateException("Mensagem inesperada: " + tipo);
                }


            }




        }catch(IOException | InterruptedException e){ throw new  RuntimeException(e);}
    }

    int threads() {
        return threads;
    }

}
