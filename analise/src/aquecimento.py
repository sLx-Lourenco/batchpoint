"""Fase 12, parte 1: quantas repetições descartar como aquecimento.

Uso: python analise/src/aquecimento.py [piloto|final]   (por omissão: final)

Lê as execuções com o nome "aquecimento" (corridas com --aquecimento 0 e
--repeticoes 50, por isso guardam todas as repetições) e desenha o tempo de cada
repetição, um painel por configuração.

Critério de estabilização (o ruído muda muito entre configurações: ~0,2 % no Mandelbrot,
~4 % na hierárquica com tarefas de 10 µs, por isso a tolerância não pode ser fixa):
  - regime: a segunda metade das repetições; referência = a mediana dela;
  - ruído: o desvio-padrão robusto do regime (1,4826 × a mediana dos desvios absolutos,
    que não se deixa levar por uma ou outra repetição muito lenta);
  - faixa = referência ± max(K_RUIDO × ruído, TOLERANCIA_MINIMA × referência);
  - o aquecimento acaba na primeira repetição r tal que ela própria está na faixa e, das
    JANELA repetições a partir de r, no máximo MAX_FORA ficam fora dela. Uma repetição
    lenta isolada mais tarde (uma em JANELA) é ruído, não aquecimento; duas ou mais
    seguidas fora da faixa, como uma curva que entra e volta a sair, ainda são aquecimento.
O valor a usar é o maior das configurações.
"""

import sys

import matplotlib.pyplot as plt

from carregar import carregar, categoria_dos_argumentos, guardar

NOME = "aquecimento"         # o <nome> dado ao correr.sh
JANELA = 10                  # repetições olhadas a partir de cada candidata
MAX_FORA = 1                 # quantas delas podem ficar fora da faixa
K_RUIDO = 3                  # tolerância = 3 desvios-padrão do ruído...
TOLERANCIA_MINIMA = 0.005    # ...mas nunca menos de 0,5 % da referência

AZUL = "#2a78d6"
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"
FAIXA = "#cde2fb"


def primeira_estavel(tempos):
    """Devolve (aquecimento sugerido, referência, tolerância) para uma série de tempos."""
    regime = tempos.iloc[len(tempos) // 2:]
    referencia = regime.median()
    ruido = 1.4826 * (regime - referencia).abs().median()
    tolerancia = max(K_RUIDO * ruido, TOLERANCIA_MINIMA * referencia)

    fora = ((tempos - referencia).abs() > tolerancia).to_numpy()
    k = len(tempos)
    for r in range(len(tempos) - JANELA + 1):
        if not fora[r] and fora[r:r + JANELA].sum() <= MAX_FORA:
            k = r
            break
    return k, referencia, tolerancia


def main():
    categoria = categoria_dos_argumentos()
    df = carregar(categoria)
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' em data/{categoria}/")

    grupos = list(df.groupby("ficheiro", sort=True))
    fig, eixos = plt.subplots(len(grupos), 1, figsize=(7, 2.4 * len(grupos)), squeeze=False)

    sugestoes = []
    for ax, (_, g) in zip(eixos[:, 0], grupos):
        g = g.sort_values("repeticao").reset_index(drop=True)
        tempos = g["tempo_s"]
        k, ref, tol = primeira_estavel(tempos)
        sugestoes.append((g["rotulo"].iloc[0], k, tol / ref))

        ax.axhspan(ref - tol, ref + tol, color=FAIXA, lw=0, zorder=0)
        ax.plot(g["repeticao"], tempos, color=AZUL, lw=2, marker="o", ms=4, zorder=2)
        ax.axvline(k - 0.5, color=TEXTO_2, lw=1, ls="--", zorder=1)
        ax.text(k - 0.2, ax.get_ylim()[1], f" descartar {k}", va="top", ha="left",
                color=TEXTO_2, fontsize=9)

        ax.set_title(g["rotulo"].iloc[0], loc="left", fontsize=10, color=TEXTO)
        ax.set_ylabel("tempo (s)", color=TEXTO_2)
        ax.grid(axis="y", color=GRELHA, lw=0.8)
        ax.tick_params(colors=TEXTO_2)
        for lado in ("top", "right"):
            ax.spines[lado].set_visible(False)
        for lado in ("left", "bottom"):
            ax.spines[lado].set_color(GRELHA)

    eixos[-1, 0].set_xlabel("repetição", color=TEXTO_2)
    fig.suptitle(f"Tempo por repetição (faixa: regime ± {K_RUIDO} desvios-padrão do ruído)",
                 x=0.01, ha="left", fontsize=11, color=TEXTO)
    fig.tight_layout()

    saida = guardar(fig, "aquecimento", categoria)

    print("Aquecimento sugerido por configuração (tolerância em % do regime):")
    for rot, k, tol in sugestoes:
        print(f"  {k:3d}  ±{tol:5.1%}  {rot}")
    print(f"\nA usar: {max(k for _, k, _ in sugestoes)} (o maior). Figura em {saida}")


if __name__ == "__main__":
    main()