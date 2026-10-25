package pt.ulisboa.batchpoint.coordenador;
import java.util.Arrays;
import pt.ulisboa.batchpoint.comum.Quadro;
import pt.ulisboa.batchpoint.protocolo.Tarefa;



public final class Distribuidor {


    private final int maxLote;
    private final Quadro<Repeticao> quadro = new Quadro<>();
    private static final Repeticao REP_FINAL = new Repeticao(new Tarefa[0]);

    Distribuidor(int lote){
        this.maxLote=lote;
    }

    public void publicar(Repeticao r){
        quadro.publicarNovo(r);
    }

    public void terminar(){
        quadro.publicarNovo(REP_FINAL);
    }

    public Entrega obterTarefas(int pedido) throws InterruptedException{

        int k = Math.min(pedido, maxLote);

        //arranque
        while (true) {
            Repeticao repAtual = quadro.atual();

            //fim
            if (repAtual == REP_FINAL){
                return null;
            }

            //normal
            if(repAtual != null){

                int inicio = repAtual.proxima.getAndAdd(k);

                if(inicio< repAtual.tarefas.length){

                    int fim = Math.min(inicio + k, repAtual.tarefas.length);


                    return new Entrega(repAtual, Arrays.asList(repAtual.tarefas).subList(inicio, fim));
                }
            }

            

            quadro.esperarDiferente(repAtual);

            
        }
    }

}
