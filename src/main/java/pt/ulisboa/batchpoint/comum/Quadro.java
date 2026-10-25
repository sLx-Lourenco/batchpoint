package pt.ulisboa.batchpoint.comum;

//para poder dps usar tanto em reps como lotes
public final class Quadro<T> {

    private volatile T objAtual = null;


    public T atual(){
        return objAtual;
    }


    synchronized public void publicarNovo(T novo){
        objAtual = novo;
        notifyAll();
    }

    synchronized public T esperarDiferente(T anterior) throws InterruptedException{
        while(anterior == objAtual || objAtual==null){
            wait();
        }

        return objAtual;
    }
}
