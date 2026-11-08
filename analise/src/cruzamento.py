"""H3: onde as duas políticas se cruzam, para cada desequilíbrio da sintética.

Uso:   python analise/src/cruzamento.py [piloto|final]      (por omissão: final)

Para cada desequilíbrio, a razão r(s) = mediana(plana) / mediana(hierárquica) em cada
tamanho de tarefa s (r > 1: a hierárquica é mais rápida). O cruzamento é o primeiro
tamanho em que r passa de > 1 para < 1, interpolado em escala log-log entre os dois
tamanhos vizinhos:  log s* = log s1 + (log s2 - log s1) * log r1 / (log r1 - log r2).

Intervalo de confiança a 95 % por bootstrap: em cada reamostragem, reamostra as
repetições de cada configuração, recalcula as medianas e o cruzamento. Se numa
reamostragem não houver cruzamento, conta à parte (a fração aparece na tabela).

A H3 pede que o cruzamento se desloque para tarefas mais pequenas quando o desequilíbrio
aumenta: compara-se o cruzamento de cada desequilíbrio com o da uniforme, e diz-se se os
intervalos se separam.

Grava data/<categoria>/cruzamento.csv e a figura das razões.
"""

import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib.ticker import FuncFormatter

from carregar import DATA, carregar, categoria_dos_argumentos, guardar

NOME = "grelha"
NS_POR_ITERACAO = 1.6755
N_BOOTSTRAP = 2000
DESEQUILIBRIOS = ("uniforme", "agrupada", "agrupada_fim", "baralhada")
ROTULO = {"uniforme": "uniforme", "agrupada": "agrupada no início",
          "agrupada_fim": "agrupada no fim", "baralhada": "baralhada"}
COR = {"uniforme": "#2a78d6", "agrupada": "#1baf7a", "agrupada_fim": "#eb6834",
       "baralhada": "#eda100"}
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"


def texto_tamanho(s):
    return f"{s * 1e6:.3g} µs" if s < 0.9e-3 else f"{s * 1e3:.3g} ms"


def cruzamento(tamanhos, razoes):
    """Primeiro cruzamento de r > 1 para r < 1, interpolado em log-log; NaN se não houver."""
    for i in range(len(razoes) - 1):
        r1, r2 = razoes[i], razoes[i + 1]
        if r1 > 1 >= r2:
            l1, l2 = np.log(tamanhos[i]), np.log(tamanhos[i + 1])
            lr1, lr2 = np.log(r1), np.log(r2)
            return float(np.exp(l1 + (l2 - l1) * lr1 / (lr1 - lr2)))
    return float("nan")


def main():
    categoria = categoria_dos_argumentos()
    df = carregar(categoria)
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json") & (df["carga"] == "sintetica")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' da sintética em data/{categoria}/")

    rng = np.random.default_rng(7)          # semente fixa: o mesmo IC em cada execução
    linhas, curvas = [], {}
    for d in DESEQUILIBRIOS:
        g = df[df["desequilibrio"] == d]
        params = sorted(g["parametro"].unique())
        amostras = []                        # por tamanho: (tempos da plana, tempos da hier.)
        for p in params:
            t = g[g["parametro"] == p]
            pl = t.loc[t["politica"] == "plana", "tempo_s"].to_numpy()
            hi = t.loc[t["politica"] == "hierarquica", "tempo_s"].to_numpy()
            if len(pl) and len(hi):
                amostras.append((p, pl, hi))
        if len(amostras) < 2:
            continue
        tam = np.array([p for p, _, _ in amostras]) * NS_POR_ITERACAO * 1e-9
        razoes = np.array([np.median(pl) / np.median(hi) for _, pl, hi in amostras])
        curvas[d] = (tam, razoes)

        s_med = cruzamento(tam, razoes)
        boot = np.empty(N_BOOTSTRAP)
        for b in range(N_BOOTSTRAP):
            r = [np.median(rng.choice(pl, len(pl))) / np.median(rng.choice(hi, len(hi)))
                 for _, pl, hi in amostras]
            boot[b] = cruzamento(tam, r)
        validos = boot[~np.isnan(boot)]
        baixo, alto = (np.percentile(validos, [2.5, 97.5]) if len(validos)
                       else (float("nan"), float("nan")))
        linhas.append({"desequilibrio": d, "cruzamento_s": s_med, "ic_baixo_s": baixo,
                       "ic_alto_s": alto, "fracao_sem_cruzamento": 1 - len(validos) / N_BOOTSTRAP})

    res = pd.DataFrame(linhas)
    if res.empty:
        sys.exit("Sem tamanhos suficientes para calcular cruzamentos.")

    print("Razão plana/hierárquica por tamanho (> 1: a hierárquica é mais rápida)")
    for d, (tam, razoes) in curvas.items():
        valores = "  ".join(f"{texto_tamanho(s)}: {r:.3f}" for s, r in zip(tam, razoes))
        print(f"  {ROTULO[d]:<20} {valores}")

    print("\nCruzamento (tamanho em que as duas políticas empatam) e IC 95 % (bootstrap)")
    ref = res.set_index("desequilibrio")
    for _, l in res.iterrows():
        if np.isnan(l["cruzamento_s"]):
            print(f"  {ROTULO[l['desequilibrio']]:<20} sem cruzamento nos tamanhos medidos")
            continue
        extra = ""
        if l["desequilibrio"] != "uniforme" and "uniforme" in ref.index:
            u = ref.loc["uniforme"]
            if l["ic_alto_s"] < u["ic_baixo_s"]:
                extra = "   <- mais pequeno do que o da uniforme (ICs separados)"
            elif l["ic_baixo_s"] > u["ic_alto_s"]:
                extra = "   <- maior do que o da uniforme (ICs separados)"
            else:
                extra = "   (IC sobreposto ao da uniforme)"
        sem = (f"; {l['fracao_sem_cruzamento']:.0%} das reamostragens sem cruzamento"
               if l["fracao_sem_cruzamento"] > 0 else "")
        print(f"  {ROTULO[l['desequilibrio']]:<20} {texto_tamanho(l['cruzamento_s']):>9}  "
              f"[{texto_tamanho(l['ic_baixo_s'])} – {texto_tamanho(l['ic_alto_s'])}]{sem}{extra}")

    csv = DATA / categoria / "cruzamento.csv"
    res.to_csv(csv, index=False)
    print(f"\nResumo em {csv}")

    fig, ax = plt.subplots(figsize=(7, 4))
    ax.axhline(1, color=TEXTO_2, lw=1, ls="--", zorder=1)
    for d, (tam, razoes) in curvas.items():
        ax.plot(tam, razoes, color=COR[d], lw=2, marker="o", ms=5, label=ROTULO[d], zorder=3)
        l = ref.loc[d]
        if not np.isnan(l["cruzamento_s"]):
            ax.plot(l["cruzamento_s"], 1, marker="|", ms=14, mew=2, color=COR[d], zorder=4)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: texto_tamanho(v)))
    ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
    ax.set_xlabel("duração de cada tarefa", color=TEXTO_2)
    ax.set_ylabel("tempo plana / tempo hierárquica", color=TEXTO_2)
    ax.set_title("Acima de 1 a hierárquica é mais rápida; traço = cruzamento",
                 loc="left", fontsize=10, color=TEXTO)
    ax.grid(color=GRELHA, lw=0.8, zorder=0)
    ax.tick_params(colors=TEXTO_2)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("left", "bottom"):
        ax.spines[lado].set_color(GRELHA)
    ax.legend(frameon=False, fontsize=9)
    fig.tight_layout()
    print(f"Figura em {guardar(fig, 'cruzamento', categoria)}")


if __name__ == "__main__":
    main()
