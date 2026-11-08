"""Figuras do relatório, ao tamanho final do template acmart (sigplan).

Uso:   python analise/src/figuras_paper.py [piloto|final]      (por omissão: final)

Desenhadas já com a largura de uma coluna (3,33 pol.) ou da página (7 pol.) e letra de
7 pt, para se lerem no PDF sem serem reduzidas. Usam os mesmos dados e contas das outras
análises (grelha.py, cruzamento.py, parado.py):
  - paper_grelha: speedup das duas políticas por desequilíbrio, mediana e IC 95 % (página);
  - paper_cruzamento: razão plana/hierárquica e o ponto de cruzamento (coluna);
  - paper_parado: fração do tempo a calcular, por ligação, em 3 configurações (coluna).
Saída em analise/figuras/<categoria>/paper_*.pdf (e .png).
"""

import sys

import matplotlib as mpl
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

from carregar import DATA, carregar, categoria_dos_argumentos, guardar
import grelha as G

COLUNA, PAGINA = 3.33, 7.0
COR = {"plana": "#2a78d6", "hierarquica": "#eb6834"}
NOME_POL = {"plana": "plana", "hierarquica": "hierárquica"}
DESEQ = ("uniforme", "agrupada", "agrupada_fim", "baralhada")
ROTULO = {"uniforme": "uniforme", "agrupada": "agrupada no início",
          "agrupada_fim": "agrupada no fim", "baralhada": "baralhada"}
COR_DESEQ = {"uniforme": "#2a78d6", "agrupada": "#1baf7a", "agrupada_fim": "#eb6834",
             "baralhada": "#eda100"}
TEXTO, TEXTO_2, GRELHA = "#0b0b0b", "#52514e", "#e4e3df"
TICKS = ([1e-5, 1e-4, 1e-3, 1e-2, 1e-1], ["10 µs", "100 µs", "1 ms", "10 ms", "100 ms"])

mpl.rcParams.update({
    "font.size": 7, "axes.titlesize": 7.5, "axes.labelsize": 7, "xtick.labelsize": 6.5,
    "ytick.labelsize": 6.5, "legend.fontsize": 6.5, "axes.edgecolor": GRELHA,
    "axes.labelcolor": TEXTO_2, "xtick.color": TEXTO_2, "ytick.color": TEXTO_2,
    "pdf.fonttype": 42,
})


def estilo(ax):
    ax.grid(axis="y", color=GRELHA, lw=0.6, zorder=0)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    ax.tick_params(length=2)


def figura_grelha(res, categoria):
    sint = res[res["carga"] == "sintetica"].copy()
    sint["duracao_s"] = sint["parametro"] * G.NS_POR_ITERACAO * 1e-9
    mand = res[res["carga"] == "mandelbrot"]

    fig, eixos = plt.subplots(1, 5, figsize=(PAGINA, 1.7), sharey=True)
    paineis = [(sint[sint["desequilibrio"] == d], "duracao_s", ROTULO[d].capitalize())
               for d in DESEQ]
    paineis.append((mand, "parametro", "Mandelbrot"))
    for ax, (dados, x, titulo) in zip(eixos, paineis):
        ax.axhline(6, color=TEXTO_2, lw=0.7, ls="--", zorder=1)
        for pol in ("plana", "hierarquica"):
            g = dados[dados["politica"] == pol].sort_values(x)
            erro = [g["speedup"] - g["speedup_ic_baixo"], g["speedup_ic_alto"] - g["speedup"]]
            ax.errorbar(g[x], g["speedup"], yerr=erro, color=COR[pol], lw=1.3, marker="o",
                        ms=2.6, capsize=1.2, elinewidth=0.7, label=NOME_POL[pol], zorder=3)
        ax.set_xscale("log")
        ax.minorticks_off()
        ax.set_title(titulo, loc="left", color=TEXTO)
        ax.set_ylim(0, 6.6)
        estilo(ax)
        if x == "duracao_s":
            ax.set_xticks(*TICKS)
            ax.tick_params(axis="x", labelsize=5.6)
            ax.set_xlabel("duração da tarefa")
        else:
            ax.set_xticks([8, 32, 128, 512], ["8", "32", "128", "512"])
            ax.set_xlabel("lado do bloco (píxeis)")
    eixos[0].set_ylabel("speedup (T1 / tempo)")
    eixos[0].text(1.1e-5, 6.08, "ideal (6)", fontsize=6, color=TEXTO_2, va="bottom")
    eixos[4].legend(frameon=False, loc="lower right", handlelength=1.5)
    fig.tight_layout(pad=0.3, w_pad=0.5)
    return guardar(fig, "paper_grelha", categoria)


def figura_cruzamento(res, categoria):
    cruz = DATA / categoria / "cruzamento.csv"
    if not cruz.exists():
        sys.exit(f"Falta {cruz}: corre primeiro o cruzamento.py")
    import pandas as pd
    c = pd.read_csv(cruz).set_index("desequilibrio")

    sint = res[res["carga"] == "sintetica"].copy()
    sint["duracao_s"] = sint["parametro"] * G.NS_POR_ITERACAO * 1e-9
    fig, ax = plt.subplots(figsize=(COLUNA, 1.85))
    ax.axhline(1, color=TEXTO_2, lw=0.7, ls="--", zorder=1)
    for d in DESEQ:
        g = sint[sint["desequilibrio"] == d]
        p = g[g["politica"] == "plana"].set_index("duracao_s")["tempo_s"]
        h = g[g["politica"] == "hierarquica"].set_index("duracao_s")["tempo_s"]
        r = (p / h).sort_index()
        r = r[r.index > 0.5e-3]             # zoom na zona do cruzamento (1 a 100 ms)
        ax.plot(r.index, r.values, color=COR_DESEQ[d], lw=1.2, marker="o", ms=2.4,
                label=ROTULO[d], zorder=3)
        if d in c.index and not np.isnan(c.loc[d, "cruzamento_s"]):
            ax.plot([c.loc[d, "ic_baixo_s"], c.loc[d, "ic_alto_s"]], [1, 1],
                    color=COR_DESEQ[d], lw=3, alpha=0.5, solid_capstyle="butt", zorder=4)
            ax.plot(c.loc[d, "cruzamento_s"], 1, marker="|", ms=9, mew=1.4,
                    color=COR_DESEQ[d], zorder=5)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.minorticks_off()
    ax.set_xticks([1e-3, 2e-3, 5e-3, 1e-2, 1e-1], ["1 ms", "2 ms", "5 ms", "10 ms", "100 ms"])
    ax.set_ylim(0.68, 1.33)
    ax.set_yticks([0.7, 0.8, 0.9, 1, 1.1, 1.2, 1.3], ["0,7", "0,8", "0,9", "1", "1,1", "1,2", "1,3"])
    ax.set_xlabel("duração da tarefa")
    ax.set_ylabel("tempo plana / hierárquica")
    estilo(ax)
    ax.text(0.99, 0.97, "acima de 1: a hierárquica é mais rápida\nabaixo de 1: a plana é mais rápida",
            transform=ax.transAxes, ha="right", va="top", fontsize=6, color=TEXTO_2)
    ax.legend(frameon=False, loc="lower left", handlelength=1.4)
    fig.tight_layout(pad=0.3)
    return guardar(fig, "paper_cruzamento", categoria)


def figura_parado(df, categoria):
    casos = [("sintetica", 5968, "uniforme", "10 µs,\nuniforme"),
             ("sintetica", 5968208, "agrupada_fim", "10 ms, agr.\nno fim"),
             ("mandelbrot", 512, "natural", "Mandelbrot,\nlado 512")]
    g = df[df["ficheiro"].str.endswith("_grelha.json")]
    fig, eixos = plt.subplots(3, 2, figsize=(COLUNA, 1.95), sharex=True)
    for i, (carga, param, deseq, rotulo) in enumerate(casos):
        for j, pol in enumerate(("plana", "hierarquica")):
            ax = eixos[i, j]
            s = g[(g["carga"] == carga) & (g["parametro"] == param)
                  & (g["desequilibrio"] == deseq) & (g["politica"] == pol)]
            oc = np.array(s["ocupado_ns"].tolist(), float)
            pa = np.clip(np.array(s["parado_ns"].tolist(), float), 0, None)
            # em cada repetição, da ligação mais ocupada à menos ocupada; depois a mediana
            f = np.median(-np.sort(-(oc / (oc + pa)), axis=1), axis=0)
            y = np.arange(len(f))
            ax.barh(y, f, color=COR[pol], height=0.75, zorder=2)
            ax.barh(y, 1 - f, left=f, color="#e9eef5", height=0.75, zorder=2)
            ax.set_yticks([])
            ax.invert_yaxis()
            ax.set_xlim(0, 1)
            for lado in ax.spines.values():
                lado.set_visible(False)
            ax.tick_params(length=0)
            ax.set_title(f"média {f.mean():.0%}", loc="right", fontsize=6, color=TEXTO_2, pad=1.5)
            if j == 0:
                ax.set_ylabel(rotulo, fontsize=6.3, color=TEXTO, rotation=0, ha="right",
                              va="center", labelpad=4)
    for ax in eixos[-1]:
        ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:.0%}"))
        ax.set_xticks([0, 0.5, 1])
    fig.text(0.6, 0.01, "fração do tempo a calcular (claro: parado)", ha="center",
             fontsize=6.5, color=TEXTO_2)
    fig.tight_layout(pad=0.3, h_pad=0.5, w_pad=1.0, rect=(0, 0.05, 1, 0.92))
    for j, pol in enumerate(("plana", "hierarquica")):
        unidade = "6 threads" if pol == "plana" else "3 nós"
        caixa = eixos[0, j].get_position()
        fig.text(caixa.x0, 0.985, f"{NOME_POL[pol]} ({unidade})", ha="left", va="top",
                 fontsize=7.5, color=TEXTO)
    return guardar(fig, "paper_parado", categoria)


def main():
    categoria = categoria_dos_argumentos()
    df = carregar(categoria)
    grelha = df[df["ficheiro"].str.endswith("_grelha.json")]
    if grelha.empty:
        sys.exit(f"Nenhuma execução 'grelha' em data/{categoria}/")
    res = G.resumir(grelha, categoria)
    for f in (figura_grelha(res, categoria), figura_cruzamento(res, categoria),
              figura_parado(df, categoria)):
        print(f"Figura em {f}")


if __name__ == "__main__":
    main()
