package pt.ulisboa.batchpoint.registo;

import pt.ulisboa.batchpoint.comum.Politica;

/**
 * A configuração de uma experiência, tal como fica registada no JSON.
 *
 * @param parametro sintética: iterações médias por tarefa; Mandelbrot: lado do bloco
 */
public record Configuracao(Politica politica, String carga, int lote, String desequilibrio,
        int tarefas, long parametro, int ligacoes, int aquecimento, int repeticoes) {
}