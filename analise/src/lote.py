"""Fase 12, parte 2: que lote usar na hierárquica, para cada tamanho de tarefa.

Lê as execuções do piloto com o nome "lote" (geradas por scripts/varrer_lote.sh). Para
cada tamanho de tarefa e cada lote, o tempo é a mediana das repetições medidas. Como os
tamanhos têm o mesmo trabalho total mas tempos diferentes, o gráfico mostra o tempo
relativo ao melhor lote desse tamanho (1,0 = o melhor), num só eixo.

Escolha: o lote mais rápido de cada tamanho; também se listam os lotes a menos de
EMPATE do melhor, porque entre esses a diferença está ao nível do ruído.
"""

import sys

import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

from carregar import FIGURAS, carregar

NOME = "lote"
EMPATE = 0.03                    # lotes a menos de 3 % do melhor contam como empatados
NS_POR_ITERACAO = 1.6755         # calibração da torre (data/calibracao/)

# cores categóricas, por ordem fixa (do mais pequeno para o maior tamanho)
CORES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300"]
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"


def tamanho(iteracoes):
    """Duração aproximada de uma tarefa, em texto: '10 µs', '1 ms'."""
    s = iteracoes * NS_POR_ITERACAO * 1e-9
    return f"{s * 1e6:.0f} µs" if s < 0.9e-3 else f"{s * 1e3:.0f} ms"


def main():
    df = carregar("piloto")
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json") & (df["politica"] == "hierarquica")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' da hierárquica em data/piloto/")

    med = (df.groupby(["parametro", "lote"])["tempo_s"].median()
             .reset_index().sort_values(["parametro", "lote"]))
    med["relativo"] = med["tempo_s"] / med.groupby("parametro")["tempo_s"].transform("min")

    fig, ax = plt.subplots(figsize=(7, 4))
    escolhas = []
    for cor, (param, g) in zip(CORES, med.groupby("parametro", sort=True)):
        nome = tamanho(param)
        ax.plot(g["lote"], g["relativo"], color=cor, lw=2, marker="o", ms=5, label=nome, zorder=3)
        melhor = g.loc[g["relativo"].idxmin()]
        ax.plot(melhor["lote"], 1.0, marker="o", ms=10, mfc="none", mec=cor, mew=2, zorder=4)
        ax.annotate(nome, (g["lote"].iloc[-1], g["relativo"].iloc[-1]), xytext=(6, 0),
                    textcoords="offset points", va="center", fontsize=9, color=TEXTO_2)
        empatados = g[g["relativo"] <= 1 + EMPATE]["lote"].tolist()
        no_limite = melhor["lote"] in (g["lote"].min(), g["lote"].max())
        escolhas.append((nome, int(melhor["lote"]), melhor["tempo_s"], empatados, g, no_limite))

    ax.set_xscale("log")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
    ax.set_xlabel("lote (tarefas por pedido)", color=TEXTO_2)
    ax.set_ylabel("tempo / tempo do melhor lote", color=TEXTO_2)
    ax.axhline(1.0, color=TEXTO_2, lw=0.8, zorder=1)
    ax.grid(color=GRELHA, lw=0.8, zorder=0)
    ax.tick_params(colors=TEXTO_2)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("left", "bottom"):
        ax.spines[lado].set_color(GRELHA)
    ax.legend(title="tarefa", frameon=False, fontsize=9, title_fontsize=9)
    ax.set_title("Hierárquica: tempo em função do lote (círculo = melhor lote)",
                 loc="left", fontsize=11, color=TEXTO)
    fig.tight_layout()

    FIGURAS.mkdir(exist_ok=True)
    saida = FIGURAS / "lote.png"
    fig.savefig(saida, dpi=200)
    fig.savefig(saida.with_suffix(".pdf"))

    print("Mediana do tempo (s) por lote:")
    for nome, _, _, _, g, _ in escolhas:
        valores = "  ".join(f"{int(l)}: {t:.3f}" for l, t in zip(g["lote"], g["tempo_s"]))
        print(f"  {nome:>7}  {valores}")
    print(f"\nMelhor lote por tamanho (empatados = a menos de {EMPATE:.0%}):")
    for nome, lote, t, empatados, _, no_limite in escolhas:
        aviso = "   <- no limite da varredura: o ótimo pode estar fora" if no_limite else ""
        print(f"  {nome:>7}  lote {lote:5d}  ({t:.3f} s)   empatados: {empatados}{aviso}")
    print(f"\nFigura em {saida}")


if __name__ == "__main__":
    main()
