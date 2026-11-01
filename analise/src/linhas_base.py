"""Fase 12, partes 3 e 4: a base de 1 core e a comparação entre os pares de cores.

Parte 3 (execuções "base1"): o tempo de cada carga com 1 nó de 1 thread, o T1 do
speedup (speedup = T1 / T6). Compara-se com o custo puro do cálculo, para saber quanto
o sistema (Docker, rede, coordenador) acrescenta mesmo sem paralelismo:
  - sintética: tarefas × iterações × NS_POR_ITERACAO (calibração);
  - Mandelbrot: o total da caracterização num core (data/caracterizacao/).

Parte 4 (execuções "nos0", "nos2", "nos4"): o mesmo trabalho com 1 nó de 2 threads em
cada par de cores. As medianas devem coincidir a menos do ruído.

Grava data/piloto/linhas_base.csv (o T1 de cada carga, para as análises seguintes) e a
figura dos pares de cores.
"""

import sys

import matplotlib.pyplot as plt

from carregar import DATA, FIGURAS, carregar

NS_POR_ITERACAO = 1.6755
MANDELBROT_1_CORE_S = 8.09       # total da caracterização num core (mandelbrot_torre.txt)

AZUL = "#2a78d6"
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"


def custo_puro(linha):
    """Segundos de cálculo puro de uma repetição desta configuração."""
    if linha["carga"] == "mandelbrot":
        return MANDELBROT_1_CORE_S
    return linha["tarefas"] * linha["parametro"] * NS_POR_ITERACAO * 1e-9


def parte3(df):
    base = df[df["ficheiro"].str.endswith("_base1.json")]
    if base.empty:
        print("Parte 3: nenhuma execução 'base1' em data/piloto/")
        return
    t1 = (base.groupby(["carga", "parametro", "tarefas"])
              .agg(t1_s=("tempo_s", "median"), rotulo=("rotulo", "first"))
              .reset_index())
    t1["puro_s"] = t1.apply(custo_puro, axis=1)
    t1["sistema"] = t1["t1_s"] / t1["puro_s"] - 1

    print("Parte 3: base de 1 core (1 nó, 1 thread)")
    print(f"  {'carga':<50} {'T1 (s)':>8} {'cálculo (s)':>12} {'sistema':>8}")
    for _, l in t1.sort_values(["carga", "tarefas"]).iterrows():
        print(f"  {l['rotulo']:<50} {l['t1_s']:8.3f} {l['puro_s']:12.3f} {l['sistema']:+8.1%}")

    saida = DATA / "piloto" / "linhas_base.csv"
    t1[["carga", "parametro", "tarefas", "t1_s", "puro_s"]].to_csv(saida, index=False)
    print(f"  T1 gravado em {saida}")


def parte4(df):
    nos = df[df["ficheiro"].str.contains(r"_nos\d\.json$")].copy()
    if nos.empty:
        print("Parte 4: nenhuma execução 'nos0/nos2/nos4' em data/piloto/")
        return
    nos["cores"] = nos["ficheiro"].str.extract(r"_nos(\d)\.json$")[0].astype(int)
    nos["cores"] = nos["cores"].map(lambda c: f"{c}-{c + 1}")
    med = nos.groupby("cores")["tempo_s"].median()
    diferenca = med.max() / med.min() - 1

    print("\nParte 4: o mesmo trabalho em cada par de cores (1 nó, 2 threads)")
    for cores, t in med.items():
        print(f"  cores {cores}: mediana {t:.4f} s")
    print(f"  diferença entre o mais lento e o mais rápido: {diferenca:.2%}")

    fig, ax = plt.subplots(figsize=(5, 3))
    pares = sorted(nos["cores"].unique())
    for x, par in enumerate(pares):
        t = nos.loc[nos["cores"] == par, "tempo_s"]
        desvio = [(i % 7 - 3) * 0.03 for i in range(len(t))]     # espalha os pontos
        ax.plot([x + d for d in desvio], t, ls="none", marker="o", ms=5,
                color=AZUL, alpha=0.6, zorder=2)
        ax.plot([x - 0.25, x + 0.25], [med[par]] * 2, color=TEXTO, lw=2, zorder=3)
    ax.set_xticks(range(len(pares)), [f"cores {p}" for p in pares])
    ax.set_ylabel("tempo (s)", color=TEXTO_2)
    ax.set_title(f"Mesmo trabalho em cada par de cores (diferença {diferenca:.1%})",
                 loc="left", fontsize=10, color=TEXTO)
    ax.grid(axis="y", color=GRELHA, lw=0.8, zorder=0)
    ax.tick_params(colors=TEXTO_2)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("left", "bottom"):
        ax.spines[lado].set_color(GRELHA)
    fig.tight_layout()
    FIGURAS.mkdir(exist_ok=True)
    saida = FIGURAS / "pares_de_cores.png"
    fig.savefig(saida, dpi=200)
    fig.savefig(saida.with_suffix(".pdf"))
    print(f"  (traço = mediana) figura em {saida}")


def main():
    try:
        df = carregar("piloto")
    except FileNotFoundError as e:
        sys.exit(str(e))
    parte3(df)
    parte4(df)


if __name__ == "__main__":
    main()
