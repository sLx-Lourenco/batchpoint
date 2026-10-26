package pt.ulisboa.batchpoint.coordenador;
import java.io.IOException;
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
        String desequilibrio = a.texto(Config.ARG_DESEQUILIBRIO, Config.COORD_DESEQUILIBRIO_POR_OMISSAO);
        int aquecimento = a.inteiro(Config.ARG_AQUECIMENTO, Config.COORD_AQUECIMENTO_POR_OMISSAO);
        int repeticoes = a.inteiro(Config.ARG_REPETICOES, Config.COORD_REPETICOES_POR_OMISSAO);
        boolean verificar = a.texto(Config.ARG_VERIFICAR, Config.COORD_VERIFICAR_POR_OMISSAO).equals("y");
        int lado = a.inteiro(Config.ARG_LADO, Config.COORD_LADO_POR_OMISSAO);

    
        //para os registos data
        String categoria = a.texto(Config.ARG_CATEGORIA, Config.REGISTO_CATEGORIA_POR_OMISSAO);
        String nome = a.texto(Config.ARG_NOME, Config.REGISTO_NOME_POR_OMISSAO);

        String sensor = a.texto(Config.ARG_SENSOR, Config.COORD_SENSOR_POR_OMISSAO);

        if(politica == Politica.PLANA && lote != 1){
            throw new IllegalArgumentException("Politica plana tem de ter 1 lote");
        }

        Tarefa[] tarefas;
        long parametro;
        String desequilibrioReg;

        switch (nomeCarga) {

            case "sintetica" -> {
                parametro = a.longo(Config.ARG_ITERACOES);
                desequilibrioReg = desequilibrio;
                tarefas = GeradorDeTarefas.gerar(nTarefas, parametro, desequilibrio,Config.COORD_SEMENTE);
            }

            case "mandelbrot" -> {
                parametro = lado;
                desequilibrioReg = Config.MANDEL_DESEQUILIBRIO;
                tarefas = GeradorDeTarefas.gerarMandelbrot(lado);
                
            }
            
            default -> {
                throw new IllegalArgumentException("Carga desconhecida: " + nomeCarga);
            }

        }
        

        // Fica a null quando não se verifica.
        Long esperado = verificar ? checksumSequencial(FabricaCargas.criar(nomeCarga), tarefas) : null;
        
        //registos
        Configuracao config = new Configuracao(politica, nomeCarga, lote, desequilibrioReg, tarefas.length, 
            parametro, ligacoes, aquecimento, repeticoes);

        RegistoJson registo = new RegistoJson(Path.of(Config.REGISTO_PASTA_POR_OMISSAO),
            categoria, nome, config, String.join(" ", args));

        

        Distribuidor dist = new Distribuidor(lote);
        

        List<Thread> threads = new ArrayList<>();
        List<Ligacao> listaLigacoes = new ArrayList<>(); 

        try (ServerSocket serverSocket = new ServerSocket(porta)){
            System.out.println("A espera de " +ligacoes + " ligacoess na porta " + porta);

            for(int i=0; i<ligacoes; i++){
                //socket da ligacao deste no
                Socket socket = serverSocket.accept();

                Canal canal = new Canal(socket);
                Ligacao ligacao = new Ligacao(canal, dist,i);
                listaLigacoes.add(ligacao);

                Thread t = new  Thread(ligacao);
                t.start();  

                threads.add(t);

            }

            System.out.println("Todas as ligacoes estabelecidas");
        }


        for(int i =0; i< aquecimento+repeticoes; i++){

            Repeticao repeticao = new Repeticao(tarefas, ligacoes);

            long inicio = System.nanoTime();
            dist.publicar(repeticao);
            repeticao.cDownLatch.await();
            long fim = System.nanoTime() - inicio;
            
            double temperatura = lerTemperatura(sensor);

            if(!repeticao.completa()){
                throw new IllegalStateException("Ha tarefas perdidas ou repetidas");
            }


            if(esperado != null && esperado!= repeticao.checkSum.get()){
                throw new IllegalStateException("Resultado diferente do sequencial");
            }

            boolean medida = i >= aquecimento;


            // tempo ocupado e parado de cada ligação nesta repetição
            long[] ocupado = new long[ligacoes];
            long[] parado = new long[ligacoes];
            long ocupadoTotal = 0;
            int threadsTotal = 0;

            for (int l = 0; l < ligacoes; l++) {
                //na plana cada ligacao e uma thread na hierarquica e um no inteiro
                int threadsLigacao = (politica == Politica.PLANA) ? 1 : listaLigacoes.get(l).threads();
                ocupado[l] = repeticao.ocupadoPorLigacao.get(l);
                parado[l] = threadsLigacao * fim - ocupado[l];
                ocupadoTotal += ocupado[l];
                threadsTotal += threadsLigacao;
            }
            double eficiencia = (double) ocupadoTotal / ((double) threadsTotal * fim);

            //-------




            //registp
            if (medida) {
                registo.escrever(i - aquecimento, fim, repeticao.mensagens.get(),
                    repeticao.bytesEnviados.get(), repeticao.somaDuracaoNs.get(),
                    ocupado, parado, eficiencia, temperatura);
            }

            System.out.printf("%s %s %3d: %10.3f ms, %d mensagens, eficiencia %5.1f%%, %.1f C%n",
                    politica.tipo(), medida ? "medida" : "aquec", i, fim / 1e6,
                    repeticao.mensagens.get(), eficiencia * 100, temperatura);
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

    /** Temperatura do CPU da torre em °C, lida por ssh; NaN se não se medir ou falhar. */
    private static double lerTemperatura(String host) {
        if (host.isEmpty()) {
            return Double.NaN;
        }
        try {
            Process p = new ProcessBuilder("ssh", host, Config.SENSOR_COMANDO).start();
            String saida = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return Integer.parseInt(saida) / 1000.0;
        } catch (IOException | InterruptedException | NumberFormatException e) {
            System.out.println("Aviso: temperatura nao lida (" + e.getMessage() + ")");
            return Double.NaN;
        }
    }

}
