package pt.ulisboa.batchpoint.cargas;

import pt.ulisboa.batchpoint.comum.Config;

public final class Sintetica implements Carga {

    @Override 
    public long executar(int id, long parametro){
        //por causa do estado 0
        long estado = id + 1L;

        for(long i=0; i<parametro;i++){
            estado = xorshift64(estado);
        }

        return  estado;
    }

    private long xorshift64(long x){
        x ^= x << Config.MARSAGLIA_A;
        x ^= x >>> Config.MARSAGLIA_B;
        x ^= x << Config.MARSAGLIA_C;

        return x;
    }
}
