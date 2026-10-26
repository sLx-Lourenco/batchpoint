package pt.ulisboa.batchpoint.cargas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pt.ulisboa.batchpoint.comum.Config;

/**
 * Testes da carga Mandelbrot (fase 10). A propriedade principal: cortar a imagem de outra
 * forma não muda nenhum píxel, por isso um bloco vale a soma dos seus sub-blocos.
 * Os blocos usados (lado 128, 32 blocos por linha):
 *   528 = linha 16, coluna 16: começa em c = (-0.5, 0), todo dentro da cardioide;
 *   536 = linha 16, coluna 24: começa em c = (0.25, 0), a ponta da cardioide (fronteira);
 *     0 = canto superior esquerdo, c = (-2, -1.5): tudo foge logo.
 * Gerados pelo Claude.
 */
class MandelbrotTest {

    private final Mandelbrot m = new Mandelbrot();

    /** Soma dos sub-blocos de lado `sub` que formam o bloco `id` de lado `lado`. */
    private long somaDosSubBlocos(int id, int lado, int sub) {
        int porLinha = Config.MANDEL_TAMANHO / lado;
        int subPorLinha = Config.MANDEL_TAMANHO / sub;
        int k = lado / sub;                                 // sub-blocos por lado do bloco
        int linha = id / porLinha, coluna = id % porLinha;
        long soma = 0;
        for (int dy = 0; dy < k; dy++) {
            for (int dx = 0; dx < k; dx++) {
                int subId = (linha * k + dy) * subPorLinha + (coluna * k + dx);
                soma += m.executar(subId, sub);
            }
        }
        return soma;
    }

    @Test
    void blocoDeFronteiraValeASomaDosSubBlocos() {
        assertEquals(m.executar(536, 128), somaDosSubBlocos(536, 128, 32), "128 = 16 blocos de 32");
        assertEquals(m.executar(536, 128), somaDosSubBlocos(536, 128, 8), "128 = 256 blocos de 8");
    }

    @Test
    void blocoQualquerValeASomaDosSubBlocos() {
        // linha 16, coluna 8: c entre -1.25 e -1.16, perto do bolbo da esquerda
        assertEquals(m.executar(520, 128), somaDosSubBlocos(520, 128, 32));
    }

    @Test
    void blocoDe1024ValeASomaDosSeusBlocosDe512() {
        // Confirma a ordem por linhas: o bloco 1 de 1024 (4 por linha) é o canto superior
        // direito, formado pelos blocos 2, 3, 10 e 11 de 512 (8 por linha).
        long esperado = m.executar(2, 512) + m.executar(3, 512) + m.executar(10, 512) + m.executar(11, 512);
        assertEquals(esperado, m.executar(1, 1024));
        assertEquals(esperado, somaDosSubBlocos(1, 1024, 512));
    }

    @Test
    void valoresDeReferencia() {
        // Valores de uma versão verificada (soma da imagem = 2 897 110 360 nos quatro lados).
        // Apanham erros que as somas de sub-blocos não veem, por serem iguais em todos os
        // tamanhos: trocar a linha com a coluna, ou calcular o novo z com o a já alterado.
        assertEquals(15_671_169L, m.executar(520, 128), "linha 16, coluna 8");
        assertEquals(128_293L, m.executar(272, 128), "linha 8, coluna 16 (o 520 transposto)");
        assertEquals(11_777_012L, m.executar(536, 128), "fronteira na ponta da cardioide");
        assertEquals(3_017_224L, m.executar(1, 1024), "bloco 1 de 1024");
    }

    @Test
    void blocoTodoDentroDoConjuntoValeLadoAoQuadradoVezesMaximo() {
        assertEquals(128L * 128 * Config.MANDEL_MAX_ITERACOES, m.executar(528, 128));
    }

    @Test
    void blocoDeFronteiraFicaEntreOsDoisExtremos() {
        long v = m.executar(536, 128);
        assertTrue(v > 128L * 128, "há pontos que demoram a fugir: " + v);
        assertTrue(v < 128L * 128 * Config.MANDEL_MAX_ITERACOES, "nem todos estão dentro: " + v);
    }

    @Test
    void cantoFogeNaPrimeiraIteracao() {
        // |c| > 2 em todo o canto: cada píxel faz 1 iteração e foge.
        assertEquals(128L * 128, m.executar(0, 128));
    }

    @Test
    void mesmoBlocoDaSempreOMesmoValor() {
        assertEquals(m.executar(536, 128), m.executar(536, 128));
    }

    @Test
    void ultimoBlocoNaoSaiDaImagem() {
        // O último bloco de lado 8 é o píxel (4088..4095, 4088..4095): tem de calcular sem erro.
        int ultimo = (Config.MANDEL_TAMANHO / 8) * (Config.MANDEL_TAMANHO / 8) - 1;
        long v = m.executar(ultimo, 8);
        assertTrue(v >= 64 && v <= 64L * Config.MANDEL_MAX_ITERACOES, "valor fora do possível: " + v);
    }

    @Test
    void fabricaCriaOMandelbrot() {
        assertTrue(FabricaCargas.criar("mandelbrot") instanceof Mandelbrot);
        assertEquals(m.executar(536, 128), FabricaCargas.criar("mandelbrot").executar(536, 128));
    }
}
