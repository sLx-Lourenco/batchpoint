package pt.ulisboa.batchpoint.coordenador;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import pt.ulisboa.batchpoint.protocolo.Tarefa;

final class Repeticao {
    final Tarefa[] tarefas;
    final AtomicInteger proxima;
    final CountDownLatch cDownLatch;
    final AtomicIntegerArray recebidos;


    final AtomicLong checkSum = new AtomicLong(0);
    final AtomicLong mensagens = new AtomicLong(0);
    final AtomicLong bytesEnviados = new AtomicLong(0);
    final AtomicLong somaDuracaoNs = new AtomicLong(0);
    final AtomicLongArray ocupadoPorLigacao;


    Repeticao(Tarefa[] tarefasRecebidas, int ligacoes){


        this.tarefas = tarefasRecebidas;
        this.proxima = new AtomicInteger(0);

        this.cDownLatch = new CountDownLatch(tarefas.length);
        this.recebidos = new AtomicIntegerArray(tarefas.length);
        this.ocupadoPorLigacao = new AtomicLongArray(ligacoes);

    }

    boolean completa(){
        for(int i=0; i< recebidos.length(); i++){

            if(recebidos.get(i) != 1){
                return false;
            }

        }
        return true;
    }
    


    
}
