package pt.ulisboa.batchpoint.ferramentas;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import pt.ulisboa.batchpoint.comum.Argumentos;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Protocolo;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

public class PingPong {


    public static void main(String[] args) throws IOException{
        Argumentos arg = new Argumentos(args);
        String modo = arg.texto("modo");


        switch (modo) {
            case "servidor" -> servidor(arg);
            case "client" -> client(arg);
                

            default -> throw new IllegalArgumentException("Modo desconhecido: " + modo);
        }



    }

    private static void servidor(Argumentos a) throws IOException{
        int porta = a.inteiro("porta", Config.SERVER_PORTA_POR_OMISSAO);

        System.out.println("A espera na porta " + porta + "...");

        try (ServerSocket servidor = new  ServerSocket(porta);
                    Canal canal = new Canal(servidor.accept());){

           while (true) { 
            byte tipo = canal.lerTipo();
                switch(tipo){

                    case Protocolo.REQUEST -> {
                        canal.lerInt();
                        canal.enviarTarefas(List.of(new Tarefa(0, 0)));
                    }

                    case Protocolo.DONE ->{
                        return;
                    }

                    default -> throw new IllegalStateException("Mensagem inesperada: " + tipo);
                }
               
           }
        }
        
    }

    private static void client(Argumentos a) throws IOException{
        InetSocketAddress address = a.endereco("servidor");

        int aquecimento = a.inteiro("aquecimento", Config.PINGPONG_AQUECIMENTO_OMISSAO);
        int medidas = a.inteiro("medidas", Config.PINGPONG_MEDIDAS_OMISSAO);
        long[] tempos = new long[medidas];

        try (Socket client = new  Socket(address.getHostString(), address.getPort());
                    Canal canal = new Canal(client)){

            for(int i =0; i<aquecimento;i++){
                idaEVolta(canal);
            }

            

            for(int i =0; i<medidas;i++){
                long inicio = System.nanoTime();
                idaEVolta(canal);
                tempos[i] = System.nanoTime()-inicio;
            }

            canal.enviarDone();

        }


        mostrarTempos(tempos, aquecimento, medidas);


    }

    private static void idaEVolta(Canal canal) throws IOException{
        canal.enviarPedido(1);
        byte tipo = canal.lerTipo();

        if (tipo != Protocolo.TASKS) {
            throw new IllegalStateException("Tipo " + tipo + " invalido");
        }

        canal.lerTarefas();
    }



    private static void mostrarTempos(long[] tempos, int aquecimento, int medidas){
        Arrays.sort(tempos);
        double mediana = tempos[tempos.length / 2] / 1000.0;
        double minimo = tempos[0] / 1000.0;
        double p99 = tempos[(int) (tempos.length * 0.99)] / 1000.0;

        System.out.printf("idas e voltas medidas: %d (aquecimento: %d)%n", medidas, aquecimento);
        System.out.printf("%12s %12s %12s%n", "minimo (µs)", "mediana (µs)", "p99 (µs)");
        System.out.printf("%12.1f %12.1f %12.1f%n", minimo, mediana, p99);
    } 

}
