package pt.ulisboa.batchpoint.no.hierarquico;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.batchpoint.cargas.Carga;
import pt.ulisboa.batchpoint.comum.Quadro;
import pt.ulisboa.batchpoint.no.AgenteNo;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

public final class NoHierarquico {

    private NoHierarquico(){}

    public static void correr(InetSocketAddress coordenador, int id, int threads, int lote, Carga carga) throws IOException, InterruptedException{
        
        Quadro<LoteLocal> quadro = new Quadro<>();
        List<Thread> listaThreads = new ArrayList<Thread>(); 

        
        for(int i=0; i<threads; i++){
            Thread t = new Thread(()->{
                calcular(quadro, carga);

            });

            t.start();
            listaThreads.add(t);


        }

        try{
            comunicar(coordenador, id, threads, lote, quadro);
        }finally{
            quadro.publicarNovo(LoteLocal.POISON);
        }
        

        for(Thread t : listaThreads){
            t.join();
        }

    }


    static private void calcular(Quadro<LoteLocal> quadro, Carga carga){
       
        try{
            while (true) {
                LoteLocal loteAtual = quadro.atual();

                //fim
                if (loteAtual == LoteLocal.POISON){
                    return;
                }

                //normal
                if(loteAtual != null){

                    int indice = loteAtual.proxima.getAndIncrement();
                    
                    if(indice< loteAtual.tarefas.size()){
                        Tarefa t = loteAtual.tarefas.get(indice);
                        
                        long inicioTimer = System.nanoTime();
                        long valor = carga.executar(t.id(), t.parametro());
                        long fimTimer = System.nanoTime()-inicioTimer;

                        loteAtual.resultados.add(new Resultado(t.id(), valor, fimTimer));
                        loteAtual.cDownLatch.countDown();

                        continue;
                    }
                }

                

                quadro.esperarDiferente(loteAtual);
            }
        }catch(InterruptedException e){ throw new  RuntimeException(e);}
    }


    static private void comunicar(InetSocketAddress coordenador, int id, int threads, int lote, 
        Quadro<LoteLocal> quadro) throws IOException ,InterruptedException{
        
        try(Canal canal = new Canal(AgenteNo.ligar(coordenador));){
            canal.enviarHello(id, threads);

            while(true){

                canal.enviarPedido(lote);
                byte tipo = canal.lerTipo();

                switch(tipo){
                    
                    case Protocolo.DONE -> {
                        //tenho de publica POISON fora para garantir
                        return;
                    }

                    case Protocolo.TASKS -> {
                        List<Tarefa> tarefas = canal.lerTarefas();

                        LoteLocal loteAtual = new LoteLocal(tarefas);

                        quadro.publicarNovo(loteAtual);
                        
                        loteAtual.cDownLatch.await();

                        canal.enviarResultados(loteAtual.resultados);

                    }

                    default -> throw new IllegalStateException("Tipo nao aceite: " + tipo);

                }


            }
        }
    }
}
