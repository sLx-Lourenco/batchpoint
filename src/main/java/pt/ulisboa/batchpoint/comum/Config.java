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
    
    private Config() {}
}
