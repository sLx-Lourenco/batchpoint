package pt.ulisboa.batchpoint.coordenador;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import pt.ulisboa.batchpoint.cargas.Carga;
import pt.ulisboa.batchpoint.cargas.FabricaCargas;
import pt.ulisboa.batchpoint.cargas.GeradorDeTarefas;
import pt.ulisboa.batchpoint.comum.Argumentos;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.comum.Politica;
import pt.ulisboa.batchpoint.protocolo.Canal;
import pt.ulisboa.batchpoint.protocolo.Tarefa;
import pt.ulisboa.batchpoint.registo.Configuracao;
import pt.ulisboa.batchpoint.registo.RegistoJson;


public class Coordenador {


    public static void main(String[] args) throws Exception{
        Argumentos a = new Argumentos(args);

        Politica politica = Politica.parse(a.texto(Config.ARG_POLITICA));
        int porta = a.inteiro(Config.ARG_PORTA, Config.SERVER_PORTA_POR_OMISSAO);
        int ligacoes = a.inteiro(Config.ARG_LIGACOES, Config.COORD_LIGACOES_POR_OMISSAO);
        int lote = a.inteiro(Config.ARG_LOTE, Config.LOTE_POR_OMISSAO);
        String nomeCarga = a.texto(Config.ARG_CARGA, Config.COORD_CARGA_POR_OMISSAO);
        int nTarefas = a.inteiro(Config.ARG_TAREFAS, Config.COORD_TAREFAS_POR_OMISSAO);
        long iteracoes = a.longo(Config.ARG_ITERACOES);
        String desequilibrio = a.texto(Config.ARG_DESEQUILIBRIO, Config.COORD_DESEQUILIBRIO_POR_OMISSAO);
        int aquecimento = a.inteiro(Config.ARG_AQUECIMENTO, Config.COORD_AQUECIMENTO_POR_OMISSAO);
        int repeticoes = a.inteiro(Config.ARG_REPETICOES, Config.COORD_REPETICOES_POR_OMISSAO);
        boolean verificar = a.texto(Config.ARG_VERIFICAR, Config.COORD_VERIFICAR_POR_OMISSAO).equals("y");
    
        //para os registos data
        String categoria = a.texto(Config.ARG_CATEGORIA, Config.REGISTO_CATEGORIA_POR_OMISSAO);
        String nome = a.texto(Config.ARG_NOME, Config.REGISTO_NOME_POR_OMISSAO);



        if(politica == Politica.PLANA && lote != 1){
            throw new IllegalArgumentException("Politica plana tem de ter 1 lote");
        }



        Tarefa[] tarefas = GeradorDeTarefas.gerar(nTarefas, iteracoes, desequilibrio,Config.COORD_SEMENTE);


        // Fica a null quando não se verifica.
        Long esperado = verificar ? checksumSequencial(FabricaCargas.criar(nomeCarga), tarefas) : null;
        
        //registos
        Configuracao config = new Configuracao(politica, nomeCarga, lote, desequilibrio, tarefas.length, 
            iteracoes, ligacoes, aquecimento, repeticoes);

        RegistoJson registo = new RegistoJson(Path.of(Config.REGISTO_PASTA_POR_OMISSAO),
            categoria, nome, config, String.join(" ", args));

        

        Distribuidor dist = new Distribuidor(lote);
        

        List<Thread> threads = new ArrayList<>();

        try (ServerSocket serverSocket = new ServerSocket(porta)){
            System.out.println("A espera de " +ligacoes + " ligacoess na porta " + porta);

            for(int i=0; i<ligacoes; i++){
                //socket da ligacao deste no
                Socket socket = serverSocket.accept();

                Canal canal = new Canal(socket);
                Ligacao ligacao = new Ligacao(canal, dist);

                Thread t = new  Thread(ligacao);
                t.start();  

                threads.add(t);

            }

            System.out.println("Todas as ligacoes estabelecidas");
        }


        for(int i =0; i< aquecimento+repeticoes; i++){

            Repeticao repeticao = new Repeticao(tarefas);

            long inicio = System.nanoTime();
            dist.publicar(repeticao);
            repeticao.cDownLatch.await();
            long fim = System.nanoTime() - inicio;
            
            if(!repeticao.completa()){
                throw new IllegalStateException("Ha tarefas perdidas ou repetidas");
            }


            if(esperado != null && esperado!= repeticao.checkSum.get()){
                throw new IllegalStateException("Resultado diferente do sequencial");
            }

            boolean medida = i >= aquecimento;

            //registp
            if (medida) {
                registo.escrever(i - aquecimento, fim, repeticao.mensagens.get(),
                        repeticao.bytesEnviados.get(), repeticao.somaDuracaoNs.get());
            }

            System.out.printf("%s %s %3d: %10.3f ms, %d mensagens%n",
                    politica.tipo(), medida ? "medida" : "aquec", i, fim / 1e6, repeticao.mensagens.get());

        }


        dist.terminar();

        for(Thread t : threads){
            //n perciso de try por causa do main
            t.join();
        }

        System.out.println("Guardado em " + registo.ficheiro());
    }


    private static long checksumSequencial(Carga carga, Tarefa[] tarefas){
        long x = 0;
        for (Tarefa t : tarefas) {
            //msm que a ligacao
            x ^= carga.executar(t.id(), t.parametro());
        }
        return x;
    }

}
