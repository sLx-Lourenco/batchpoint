package pt.ulisboa.batchpoint.no;

import java.net.InetSocketAddress;

import pt.ulisboa.batchpoint.comum.Argumentos;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.comum.Politica;

public class AgenteNo {

    public static void main(String[] args){
        Argumentos a = new Argumentos(args);

        String modo = a.texto("modo");
        Politica.parse(modo);

        int id = a.inteiro("id");
        int threads = a.inteiro("threads", Config.THREADS_POR_OMISSAO);
        InetSocketAddress coordenador = a.endereco("coordenador");


        System.out.print(modo + " : " + id + " : " + threads +" : "+ coordenador.getHostString() + ":" + coordenador.getPort() + "\n");

    }
}
