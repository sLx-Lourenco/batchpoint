package pt.ulisboa.batchpoint.comum;

public final class Config {
    public static final int LOTE_POR_OMISSAO=1;
    public static final int THREADS_POR_OMISSAO = 2;

    public static final int MARSAGLIA_A=13;
    public static final int MARSAGLIA_B=7;
    public static final int MARSAGLIA_C=17;

    public static final long CALIBRACAO_AQUECIMENTO_NS = 3_000_000_000L;
    public static final long CALIBRACAO_ITERACOES_AQUECIMENTO = 100_000;
    public static final long[] CALIBRACAO_ALVOS_NS = {10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L};
    public static final int CALIBRACAO_REPETICOES = 10;
    public static final long CALIBRACAO_ITERACOES_MEDICAO = 100_000_000L;

    public static final long CALIBRACAO_CONFIRMACAO_NS = 1_000_000_000L;


    public static final int TAREFA_FRACAO_LONGAS = 10;
    //longa 10x maior que a curta
    public static final int TAREFA_FATOR_LONGA = 10;
    // 0,9 × c + 0,1 × 10c = m
    public static final double TAREFA_DIVISOR_CURTA = 1.9;

    public static final int SERVER_PORTA_POR_OMISSAO = 9000;

    public static final int PINGPONG_AQUECIMENTO_OMISSAO = 1000;
    public static final int PINGPONG_MEDIDAS_OMISSAO = 10_000;
    
    private Config() {}
}
