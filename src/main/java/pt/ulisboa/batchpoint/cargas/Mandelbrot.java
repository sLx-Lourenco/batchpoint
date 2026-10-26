package pt.ulisboa.batchpoint.cargas;

import pt.ulisboa.batchpoint.comum.Config;

public final class Mandelbrot implements Carga {

    @Override 
    public long executar(int id, long parametro){
        int lado = (int) parametro;

        int blocosPorLinha = Config.MANDEL_TAMANHO / lado;

        int blocoLinha = id/blocosPorLinha;//y
        int blocoColuna = id % blocosPorLinha;//x

        int pixelX = blocoColuna * lado;
        int pixelY = blocoLinha * lado;

        double passo = Config.MANDEL_LARGURA / Config.MANDEL_TAMANHO;
        long soma = 0;

        for(int i=pixelY; i < pixelY + lado;i++){//y
            double cIm = Config.MANDEL_Y_MIN + i * passo;

            for(int j=pixelX; j < pixelX + lado; j++){//x
                double cRe = Config.MANDEL_X_MIN + j * passo;

                soma+= iteracoes(cRe, cIm);

            }
        }

        return soma;

    }

    private int iteracoes(double cRe, double cIm){
        double a = 0;
        double b = 0;

        for(int n=0; n<Config.MANDEL_MAX_ITERACOES; n++){

            //ponto sem retorno dado pelo teorema
            if(a*a + b*b > 4){
                return n;
            }

            double aTemp = a*a - b*b + cRe;
            b = 2*a*b + cIm;
            a= aTemp;

        }

        return Config.MANDEL_MAX_ITERACOES;
    }



}
