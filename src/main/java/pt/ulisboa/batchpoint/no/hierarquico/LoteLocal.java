package pt.ulisboa.batchpoint.no.hierarquico;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import pt.ulisboa.batchpoint.protocolo.Resultado;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

final class LoteLocal {

    static final LoteLocal POISON = new LoteLocal(List.of());

    final List<Tarefa> tarefas;
    final AtomicInteger proxima;
    final CountDownLatch cDownLatch;

    final List<Resultado> resultados;

    //vem do TASKS
    LoteLocal(List<Tarefa> tarefasRecebida){
        this.tarefas= tarefasRecebida;
        this.cDownLatch = new CountDownLatch(tarefasRecebida.size());
        this.proxima = new AtomicInteger(0);
        List<Resultado> r = new ArrayList<>(tarefasRecebida.size());

        this.resultados = Collections.synchronizedList(r);
    }


}
