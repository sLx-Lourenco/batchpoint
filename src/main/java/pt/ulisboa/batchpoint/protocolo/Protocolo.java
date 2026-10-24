package pt.ulisboa.batchpoint.protocolo;

public final class Protocolo {
    
    //como vai pela rede em vez de int q ocupa 4 bytes byte ocupa so 1
    //no->coordenador
    public static final byte HELLO = 1;
    //no->coordenador
    public static final byte REQUEST = 2; 
    //coordenador->no
    public static final byte TASKS = 3;
    //no->coordenador
    public static final byte RESULTS = 4;
    //coordenador->no
    public static final byte DONE = 5;


    /*
    HELLO: id do no, numero de threads
    REQUEST: maximo de tarefas que aceita
    TASKS: n, seguido de n × (id, parametro)
    RESULTS: n, seguido de n × (id, valor, duracao)
    DONE: nada
    */

    private Protocolo() {}


}
