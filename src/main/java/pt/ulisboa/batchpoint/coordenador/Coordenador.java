package pt.ulisboa.batchpoint.coordenador;


import pt.ulisboa.batchpoint.comum.Argumentos;
import pt.ulisboa.batchpoint.comum.Config;
import pt.ulisboa.batchpoint.comum.Politica;


public class Coordenador {


    public static void main(String[] args){
        Argumentos a = new Argumentos(args);

        String politica = a.texto("politica");
        Politica.parse(politica);

        int lote = a.inteiro("lote", Config.LOTE_POR_OMISSAO);


        System.out.print(politica + " : " + lote + "\n");
    }

}
