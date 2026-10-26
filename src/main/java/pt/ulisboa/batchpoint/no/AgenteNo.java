package pt.ulisboa.batchpoint.no;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;

import pt.ulisboa.batchpoint.cargas.Carga;
import pt.ulisboa.batchpoint.cargas.FabricaCargas;
import pt.ulisboa.batchpoint.comum.Argumentos;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.comum.Politica;
import pt.ulisboa.batchpoint.no.hierarquico.NoHierarquico;
import pt.ulisboa.batchpoint.no.plano.NoPlano;

public class AgenteNo {

    public static void main(String[] args) throws Exception{
        Argumentos a = new Argumentos(args);

        String modo = a.texto(Config.ARG_MODO);
        Politica politica = Politica.parse(modo);

        int id = a.inteiro(Config.ARG_ID);

        int threads = a.inteiro(Config.ARG_THREADS, Config.THREADS_POR_OMISSAO);

        InetSocketAddress coordenador = a.endereco(Config.ARG_COORDENADOR);

        String carga = a.texto(Config.ARG_CARGA, Config.COORD_CARGA_POR_OMISSAO);

        Carga cargasCriadas = FabricaCargas.criar(carga);

        int lote = a.inteiro(Config.ARG_LOTE, Config.COORD_LOTE_POR_OMISSAO);

        System.out.print(modo + " : " + id + " : " + threads +" : "+ coordenador.getHostString() + ":" + coordenador.getPort() + "\n");



        switch(politica){

            case PLANA -> { 
                NoPlano.correr(coordenador, id, threads, cargasCriadas);
            }

            case HIERARQUICA -> {
                NoHierarquico.correr(coordenador, id, threads, lote, cargasCriadas);
            }



            default -> throw new IllegalArgumentException("Politica nao aceite: " + modo);


        }

    }

    public static Socket ligar(InetSocketAddress coordenador) throws IOException, InterruptedException{
        int cont = 0;

        while(true){
            try{
                Socket socket = new Socket(coordenador.getHostString(), coordenador.getPort());
                return socket;
            }catch(ConnectException e){
                cont++;

                if(cont>Config.NO_MAX_TENTATIVAS){
                    throw e;
                }else{
                    Thread.sleep(Config.NO_INTERVALO_TENTATIVAS_MS);
                }

            }
        }

    }
}
