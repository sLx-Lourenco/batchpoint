"""Fase 12, parte 5: os resultados da grelha (piloto ou final).

Uso:   python analise/src/grelha.py [piloto|final]      (por omissão: piloto)

Para cada configuração (política × carga × tamanho × desequilíbrio), o tempo é a mediana
das repetições medidas. O speedup é T1 / tempo, com o T1 da base de 1 core
(data/piloto/linhas_base.csv, mesma carga e tamanho); o ideal com 6 cores é 6.
A eficiência é a média da eficiência medida em cada repetição (fase 11).

Gera uma figura com quatro painéis (sintética uniforme, agrupada e baralhada, e o
Mandelbrot) e imprime a tabela das duas políticas lado a lado.
"""

import sys

import matplotlib.pyplot as plt
import pandas as pd
from matplotlib.ticker import FuncFormatter

from carregar import DATA, FIGURAS, carregar

NOME = "grelha"
CORES_MAX = 6
NS_POR_ITERACAO = 1.6755

COR = {"plana": "#2a78d6", "hierarquica": "#eb6834"}
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"


def tamanho(iteracoes):
    s = iteracoes * NS_POR_ITERACAO * 1e-9
    return f"{s * 1e6:.0f} µs" if s < 0.9e-3 else f"{s * 1e3:.0f} ms"


def resumir(df):
    """Uma linha por configuração: mediana do tempo, eficiência média, speedup."""
    chaves = ["politica", "carga", "parametro", "tarefas", "desequilibrio", "lote"]
    res = (df.groupby(chaves)
             .agg(tempo_s=("tempo_s", "median"),
                  eficiencia=("eficiencia", "mean"),
                  repeticoes=("tempo_s", "size"),
                  temp_max=("temperatura_c", "max"))
             .reset_index())
    base = pd.read_csv(DATA / "piloto" / "linhas_base.csv")
    res = res.merge(base[["carga", "parametro", "tarefas", "t1_s"]],
                    on=["carga", "parametro", "tarefas"], how="left")
    res["speedup"] = res["t1_s"] / res["tempo_s"]
    return res


def estilo(ax):
    ax.grid(color=GRELHA, lw=0.8, zorder=0)
    ax.tick_params(colors=TEXTO_2, labelsize=8)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("left", "bottom"):
        ax.spines[lado].set_color(GRELHA)


def painel(ax, dados, eixo_x, titulo, rotulo_x):
    ax.axhline(CORES_MAX, color=TEXTO_2, lw=1, ls="--", zorder=1)
    ax.text(0.01, CORES_MAX, " ideal", transform=ax.get_yaxis_transform(),
            va="bottom", ha="left", fontsize=8, color=TEXTO_2)
    for pol in ("plana", "hierarquica"):
        g = dados[dados["politica"] == pol].sort_values(eixo_x)
        if g.empty:
            continue
        ax.plot(g[eixo_x], g["speedup"], color=COR[pol], lw=2, marker="o", ms=5,
                label=pol, zorder=3)
    ax.set_xscale("log")
    ax.set_title(titulo, loc="left", fontsize=10, color=TEXTO)
    ax.set_xlabel(rotulo_x, color=TEXTO_2, fontsize=9)
    ax.set_ylim(0, CORES_MAX + 0.6)
    estilo(ax)


def figura(res, categoria):
    sint = res[res["carga"] == "sintetica"].copy()
    sint["duracao_s"] = sint["parametro"] * NS_POR_ITERACAO * 1e-9
    mand = res[res["carga"] == "mandelbrot"]

    fig, eixos = plt.subplots(2, 2, figsize=(9, 6.5), sharey=True)
    for ax, d in zip(eixos.flat, ("uniforme", "agrupada", "baralhada")):
        painel(ax, sint[sint["desequilibrio"] == d], "duracao_s",
               f"Sintética, {d}", "duração de cada tarefa")
        ax.xaxis.set_major_formatter(FuncFormatter(
            lambda v, _: f"{v * 1e6:g} µs" if v < 0.9e-3 else f"{v * 1e3:g} ms"))
    ax = eixos[1, 1]
    painel(ax, mand, "parametro", "Mandelbrot (desequilíbrio natural)", "lado do bloco (píxeis)")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
    ax.set_xticks(sorted(mand["parametro"].unique()) if not mand.empty else [])
    ax.minorticks_off()
    for a in eixos[:, 0]:
        a.set_ylabel("speedup (T1 / tempo)", color=TEXTO_2, fontsize=9)
    eixos[0, 1].legend(frameon=False, fontsize=9, loc="lower right")
    fig.suptitle(f"Grelha ({categoria}): speedup com 6 cores (3 nós × 2 threads)",
                 x=0.01, ha="left", fontsize=11, color=TEXTO)
    fig.tight_layout()

    FIGURAS.mkdir(exist_ok=True)
    saida = FIGURAS / f"grelha_{categoria}.png"
    fig.savefig(saida, dpi=200)
    fig.savefig(saida.with_suffix(".pdf"))
    return saida


def tabela(res):
    res = res.copy()
    res["config"] = res.apply(
        lambda l: (f"Mandelbrot lado {l['parametro']}" if l["carga"] == "mandelbrot"
                   else f"{tamanho(l['parametro'])} {l['desequilibrio']}"), axis=1)
    lado = res.pivot_table(index=["carga", "parametro", "desequilibrio", "config"],
                           columns="politica", values=["tempo_s", "speedup", "eficiencia"])
    print(f"{'configuração':<26} {'plana':>16} {'hierárquica':>16}   vencedora")
    print(f"{'':<26} {'tempo  sp  efic':>16} {'tempo  sp  efic':>16}")
    for chave, l in lado.sort_index().iterrows():
        partes = []
        for pol in ("plana", "hierarquica"):
            t = l.get(("tempo_s", pol))
            if pd.isna(t):
                partes.append(f"{'—':>16}")
            else:
                partes.append(f"{t:6.3f} {l[('speedup', pol)]:4.1f} {l[('eficiencia', pol)]:4.0%}")
        tp, th = l.get(("tempo_s", "plana")), l.get(("tempo_s", "hierarquica"))
        if pd.isna(tp) or pd.isna(th):
            venc = ""
        elif tp < th:
            venc = f"plana ({th / tp:.2f}×)"
        else:
            venc = f"hierárquica ({tp / th:.2f}×)"
        print(f"{chave[3]:<26} {partes[0]:>16} {partes[1]:>16}   {venc}")


def main():
    categoria = sys.argv[1] if len(sys.argv) > 1 else "piloto"
    df = carregar(categoria)
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' em data/{categoria}/")

    res = resumir(df)
    if res["t1_s"].isna().any():
        print("Aviso: sem T1 para algumas configurações (falta a base de 1 core).")
    tabela(res)
    t = res["temp_max"].max()
    print(f"\n{len(res)} configurações, {res['repeticoes'].min()}–{res['repeticoes'].max()} "
          f"repetições cada; temperatura máxima {t:.1f} °C")
    print(f"Figura em {figura(res, categoria)}")


if __name__ == "__main__":
    main()
