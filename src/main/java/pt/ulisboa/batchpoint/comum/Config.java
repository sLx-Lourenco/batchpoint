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




    // ---------- nomes dos argumentos (o que vem depois de --) ----------
    public static final String ARG_POLITICA = "politica";
    public static final String ARG_LOTE = "lote";
    public static final String ARG_PORTA = "porta";
    public static final String ARG_LIGACOES = "ligacoes";
    public static final String ARG_CARGA = "carga";
    public static final String ARG_TAREFAS = "tarefas";
    public static final String ARG_ITERACOES = "iteracoes";
    public static final String ARG_DESEQUILIBRIO = "desequilibrio";
    public static final String ARG_AQUECIMENTO = "aquecimento";
    public static final String ARG_REPETICOES = "repeticoes";
    public static final String ARG_VERIFICAR = "verificar";
    public static final String ARG_MODO = "modo";
    public static final String ARG_ID = "id";
    public static final String ARG_THREADS = "threads";
    public static final String ARG_COORDENADOR = "coordenador";
    public static final String ARG_SERVIDOR = "servidor";
    public static final String ARG_MEDIDAS = "medidas";

    // ---------- omissões do coordenador ----------
    public static final int COORD_LIGACOES_POR_OMISSAO = 6;
    public static final String COORD_CARGA_POR_OMISSAO = "sintetica";
    public static final int COORD_TAREFAS_POR_OMISSAO = 1000;
    public static final String COORD_DESEQUILIBRIO_POR_OMISSAO = "uniforme";
    public static final int COORD_AQUECIMENTO_POR_OMISSAO = 5;
    public static final int COORD_REPETICOES_POR_OMISSAO = 30;
    public static final String COORD_VERIFICAR_POR_OMISSAO = "n";
    public static final long COORD_SEMENTE = 42L;
    public static final int COORD_LOTE_POR_OMISSAO = 1;


    // ---------- ligação do nó ao coordenador ----------
    public static final long NO_INTERVALO_TENTATIVAS_MS = 200;
    public static final int NO_MAX_TENTATIVAS = 150;   // 150 × 200 ms = 30 s

    // ---------- registo dos resultados ----------
    public static final String ARG_CATEGORIA = "categoria";
    public static final String ARG_NOME = "nome";
    public static final String REGISTO_CATEGORIA_POR_OMISSAO = "testes";
    public static final String REGISTO_NOME_POR_OMISSAO = "";
    public static final String REGISTO_PASTA_POR_OMISSAO = "data";
    
    private Config() {}
}
