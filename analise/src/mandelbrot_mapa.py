"""O custo dos blocos do Mandelbrot: porque é que o desequilíbrio é "natural".

Uso:   python analise/src/mandelbrot_mapa.py

Lê data/caracterizacao/mandelbrot_blocos.csv, gravado pela caracterização em Java
(ferramentas/CaracterizacaoMandelbrot, corrida na torre presa a um core): uma linha por
bloco de cada lado, com as iterações (o custo exato) e a duração medida.
Verifica que a soma das iterações de cada lado é a da imagem inteira (2 897 110 360).

Desenha duas figuras (em analise/figuras/caracterizacao/):
  - mandelbrot_mapa: o custo de cada bloco de lado 128, com a grelha dos blocos de 512
    e a percentagem do trabalho total em cada um;
  - mandelbrot_concentracao: para cada lado, a fração do tempo total concentrada nas
    tarefas mais caras (ordenadas da mais cara para a mais barata).
"""

import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib.colors import LinearSegmentedColormap, LogNorm
from matplotlib.ticker import FuncFormatter

from carregar import DATA, guardar

FICHEIRO = DATA / "caracterizacao" / "mandelbrot_blocos.csv"
SOMA_IMAGEM = 2_897_110_360
LADO_MAPA = 128                 # resolução do mapa (32 × 32 blocos)
LADO_GRELHA = 512               # grelha sobreposta (8 × 8 blocos)

TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"
AZUL = LinearSegmentedColormap.from_list("azul", ["#eef5fd", "#9ec5f1", "#2a78d6", "#0b2f60"])
CORES_LADO = {512: "#2a78d6", 128: "#eb6834", 32: "#1baf7a", 8: "#eda100"}


def carregar_blocos():
    if not FICHEIRO.exists():
        sys.exit(f"Falta {FICHEIRO}: corre primeiro a caracterização em Java")
    df = pd.read_csv(FICHEIRO)
    for lado, g in df.groupby("lado"):
        if g["iteracoes"].sum() != SOMA_IMAGEM:
            sys.exit(f"Lado {lado}: soma {g['iteracoes'].sum()} diferente de {SOMA_IMAGEM}")
    return df


def figura_mapa(df):
    m = df[df["lado"] == LADO_MAPA]
    custos = m.pivot(index="linha", columns="coluna", values="iteracoes").to_numpy()
    g = df[df["lado"] == LADO_GRELHA]
    fracao = (g.pivot(index="linha", columns="coluna", values="iteracoes") / SOMA_IMAGEM).to_numpy()

    ordenado = np.sort(fracao.ravel())[::-1]
    print(f"Blocos de lado {LADO_GRELHA}: o mais caro tem {ordenado[0]:.1%} do trabalho; "
          f"os 8 mais caros, {ordenado[:8].sum():.0%}; os 32 mais baratos, {ordenado[32:].sum():.1%}")
    print("Trabalho por linha de blocos de 512: " + "  ".join(f"{p:.0%}" for p in fracao.sum(axis=1)))

    n, k = custos.shape[0], LADO_GRELHA // LADO_MAPA
    fig, ax = plt.subplots(figsize=(6.4, 5.6))
    imagem = ax.imshow(custos, cmap=AZUL, norm=LogNorm(vmin=max(custos.min(), 1), vmax=custos.max()),
                       extent=(0, n, n, 0), interpolation="nearest")
    for i in range(0, n + 1, k):
        ax.axhline(i, color="white", lw=1.2)
        ax.axvline(i, color="white", lw=1.2)
    for li in range(fracao.shape[0]):
        for co in range(fracao.shape[1]):
            p = fracao[li, co]
            if p >= 0.005:
                ax.text(co * k + k / 2, li * k + k / 2, f"{p:.0%}" if p >= 0.01 else "<1%",
                        ha="center", va="center", fontsize=8,
                        color="white" if p > 0.02 else TEXTO)
    ax.set_xticks([]), ax.set_yticks([])
    for lado in ax.spines.values():
        lado.set_visible(False)
    barra = fig.colorbar(imagem, ax=ax, fraction=0.046, pad=0.03)
    barra.set_label(f"iterações por bloco de {LADO_MAPA}×{LADO_MAPA} (escala log)",
                    color=TEXTO_2, fontsize=9)
    barra.ax.tick_params(colors=TEXTO_2, labelsize=8)
    ax.set_title(f"Custo de cada bloco do Mandelbrot (grelha branca: blocos de {LADO_GRELHA}, "
                 f"% do trabalho total)", loc="left", fontsize=10, color=TEXTO)
    fig.tight_layout()
    return guardar(fig, "mandelbrot_mapa", "caracterizacao")


def figura_concentracao(df):
    fig, ax = plt.subplots(figsize=(6.4, 4.2))
    ax.plot([0, 1], [0, 1], color=TEXTO_2, lw=1, ls="--", zorder=1)
    ax.text(0.62, 0.55, "todas as tarefas iguais", color=TEXTO_2, fontsize=8, rotation=33)

    print(f"\n{'lado':>5} {'tarefas':>8} {'tempo total':>12} {'média/mediana':>14} "
          f"{'tarefas c/ 50 % do tempo':>25} {'c/ 90 %':>9}")
    for lado in sorted(df["lado"].unique(), reverse=True):
        d = np.sort(df.loc[df["lado"] == lado, "duracao_ns"].to_numpy())[::-1]
        acumulado = np.concatenate([[0], np.cumsum(d) / d.sum()])
        x = np.arange(len(acumulado)) / len(d)
        ax.plot(x, acumulado, color=CORES_LADO.get(lado, TEXTO), lw=2, label=f"lado {lado}", zorder=3)
        f50 = np.searchsorted(acumulado, 0.5) / len(d)
        f90 = np.searchsorted(acumulado, 0.9) / len(d)
        print(f"{lado:5d} {len(d):8d} {d.sum() / 1e9:11.2f}s {d.mean() / np.median(d):14.0f} "
              f"{f50:25.1%} {f90:9.1%}")

    ax.set_xlim(0, 1), ax.set_ylim(0, 1.02)
    pct = FuncFormatter(lambda v, _: f"{v:.0%}")
    ax.xaxis.set_major_formatter(pct), ax.yaxis.set_major_formatter(pct)
    ax.set_xlabel("fração das tarefas (da mais cara para a mais barata)", color=TEXTO_2, fontsize=9)
    ax.set_ylabel("fração do tempo total", color=TEXTO_2, fontsize=9)
    ax.grid(color=GRELHA, lw=0.8, zorder=0)
    ax.tick_params(colors=TEXTO_2, labelsize=8)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("left", "bottom"):
        ax.spines[lado].set_color(GRELHA)
    ax.legend(frameon=False, fontsize=9, loc="lower right")
    ax.set_title("Concentração do trabalho do Mandelbrot (tempo medido num core)",
                 loc="left", fontsize=10, color=TEXTO)
    fig.tight_layout()
    return guardar(fig, "mandelbrot_concentracao", "caracterizacao")


def main():
    df = carregar_blocos()
    print(f"Soma das iterações de cada lado: {SOMA_IMAGEM:,} (confere)".replace(",", " "))
    print(f"Figura em {figura_mapa(df)}")
    print(f"Figura em {figura_concentracao(df)}")


if __name__ == "__main__":
    main()
