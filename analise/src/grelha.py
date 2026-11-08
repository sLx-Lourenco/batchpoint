"""Fase 12, parte 5: os resultados da grelha.

Uso:   python analise/src/grelha.py [piloto|final]      (por omissão: final)

Para cada configuração (política × carga × tamanho × desequilíbrio):
  - tempo: a mediana das repetições medidas, com o intervalo de confiança a 95 % dessa
    mediana (bootstrap: N_BOOTSTRAP reamostragens das repetições);
  - speedup = T1 / tempo, com o T1 da base de 1 core (data/<categoria>/linhas_base.csv,
    mesma carga e tamanho); o ideal com 6 cores é 6. O intervalo do speedup é o do tempo
    invertido (a incerteza do T1, ~0,5 %, não entra);
  - eficiência: a média da eficiência medida em cada repetição (fase 11);
  - comunicação: mensagens por repetição e bytes enviados pelo coordenador (as TASKS).

Imprime duas tabelas (desempenho e comunicação), grava-as em CSV ao lado dos dados e
desenha um painel por desequilíbrio da sintética e um para o Mandelbrot.
"""

import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib.ticker import FuncFormatter

from carregar import DATA, carregar, categoria_dos_argumentos, guardar

NOME = "grelha"
CORES_MAX = 6
NS_POR_ITERACAO = 1.6755
N_BOOTSTRAP = 2000
CHAVES = ["politica", "carga", "parametro", "tarefas", "desequilibrio", "lote"]

COR = {"plana": "#2a78d6", "hierarquica": "#eb6834"}
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"
DESEQUILIBRIOS = ("uniforme", "agrupada", "agrupada_fim", "baralhada")
ROTULO = {"uniforme": "uniforme", "agrupada": "agrupada no início",
          "agrupada_fim": "agrupada no fim", "baralhada": "baralhada"}


def tamanho(iteracoes):
    s = iteracoes * NS_POR_ITERACAO * 1e-9
    return f"{s * 1e6:.0f} µs" if s < 0.9e-3 else f"{s * 1e3:.0f} ms"


def ic_mediana(valores, rng):
    """Intervalo de confiança a 95 % da mediana, por bootstrap."""
    v = np.asarray(valores)
    medianas = np.median(rng.choice(v, size=(N_BOOTSTRAP, len(v)), replace=True), axis=1)
    return np.percentile(medianas, [2.5, 97.5])


def resumir(df, categoria):
    """Uma linha por configuração."""
    rng = np.random.default_rng(12)          # semente fixa: o mesmo IC em cada execução
    linhas = []
    for chave, g in df.groupby(CHAVES):
        baixo, alto = ic_mediana(g["tempo_s"], rng)
        linhas.append({
            **dict(zip(CHAVES, chave)),
            "tempo_s": g["tempo_s"].median(),
            "tempo_ic_baixo": baixo,
            "tempo_ic_alto": alto,
            "eficiencia": g["eficiencia"].mean(),
            "mensagens": g["mensagens"].median(),
            "bytes": g["bytes_enviados"].median(),
            "repeticoes": len(g),
            "temp_max": g["temperatura_c"].max(),
        })
    res = pd.DataFrame(linhas)

    ficheiro_base = DATA / categoria / "linhas_base.csv"
    if not ficheiro_base.exists():
        sys.exit(f"Falta {ficheiro_base}: corre primeiro analise/src/linhas_base.py {categoria}")
    base = pd.read_csv(ficheiro_base)
    res = res.merge(base[["carga", "parametro", "tarefas", "t1_s"]],
                    on=["carga", "parametro", "tarefas"], how="left")
    res["speedup"] = res["t1_s"] / res["tempo_s"]
    res["speedup_ic_baixo"] = res["t1_s"] / res["tempo_ic_alto"]
    res["speedup_ic_alto"] = res["t1_s"] / res["tempo_ic_baixo"]
    res["config"] = res.apply(
        lambda l: (f"Mandelbrot lado {l['parametro']}" if l["carga"] == "mandelbrot"
                   else f"{tamanho(l['parametro'])} {l['desequilibrio']}"), axis=1)
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
        erro = [g["speedup"] - g["speedup_ic_baixo"], g["speedup_ic_alto"] - g["speedup"]]
        ax.errorbar(g[eixo_x], g["speedup"], yerr=erro, color=COR[pol], lw=2, marker="o",
                    ms=5, capsize=3, elinewidth=1.2, label=pol, zorder=3)
    ax.set_xscale("log")
    ax.set_title(titulo, loc="left", fontsize=10, color=TEXTO)
    ax.set_xlabel(rotulo_x, color=TEXTO_2, fontsize=9)
    ax.set_ylim(0, CORES_MAX + 0.6)
    estilo(ax)


def figura(res, categoria):
    sint = res[res["carga"] == "sintetica"].copy()
    sint["duracao_s"] = sint["parametro"] * NS_POR_ITERACAO * 1e-9
    mand = res[res["carga"] == "mandelbrot"]

    fig, eixos = plt.subplots(2, 3, figsize=(12, 6.5), sharey=True)
    for ax, d in zip(eixos.flat, DESEQUILIBRIOS):
        painel(ax, sint[sint["desequilibrio"] == d], "duracao_s",
               f"Sintética, {ROTULO[d]}", "duração de cada tarefa")
        ax.xaxis.set_major_formatter(FuncFormatter(
            lambda v, _: f"{v * 1e6:g} µs" if v < 0.9e-3 else f"{v * 1e3:g} ms"))
    eixos[1, 2].axis("off")
    ax = eixos[1, 1]
    painel(ax, mand, "parametro", "Mandelbrot (desequilíbrio natural)", "lado do bloco (píxeis)")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
    ax.set_xticks(sorted(mand["parametro"].unique()) if not mand.empty else [])
    ax.minorticks_off()
    for a in eixos[:, 0]:
        a.set_ylabel("speedup (T1 / tempo)", color=TEXTO_2, fontsize=9)
    eixos[0, 1].legend(frameon=False, fontsize=9, loc="lower right")
    lo, hi = int(res["repeticoes"].min()), int(res["repeticoes"].max())
    n = f"{lo}" if lo == hi else f"{lo}–{hi}"
    fig.suptitle(f"Speedup com 6 cores (3 nós × 2 threads), mediana de {n} repetições "
                 f"e IC 95 %", x=0.01, ha="left", fontsize=11, color=TEXTO)
    fig.tight_layout()
    return guardar(fig, "grelha", categoria)


def lado_a_lado(res):
    """Uma linha por configuração, com as duas políticas em colunas."""
    ordem = ["carga", "parametro", "desequilibrio", "config"]
    return res.pivot_table(index=ordem, columns="politica",
                           values=["tempo_s", "tempo_ic_baixo", "tempo_ic_alto", "speedup",
                                   "eficiencia", "mensagens", "bytes"]).sort_index()


def tabelas(res):
    t = lado_a_lado(res)

    def v(l, campo, pol):
        x = l.get((campo, pol))
        return None if x is None or pd.isna(x) else x

    print("Desempenho (tempo = mediana [IC 95 %]; sp = speedup; ef = eficiência)")
    print(f"{'configuração':<22} {'plana':^30} {'hierárquica':^30}  vencedora")
    for chave, l in t.iterrows():
        partes = []
        for pol in ("plana", "hierarquica"):
            tm = v(l, "tempo_s", pol)
            if tm is None:
                partes.append(f"{'—':^30}")
                continue
            ic = f"[{v(l, 'tempo_ic_baixo', pol):.3f}–{v(l, 'tempo_ic_alto', pol):.3f}]"
            partes.append(f"{tm:6.3f} {ic:<15} {v(l, 'speedup', pol):3.1f} {v(l, 'eficiencia', pol):4.0%}")
        tp, th = v(l, "tempo_s", "plana"), v(l, "tempo_s", "hierarquica")
        if tp is None or th is None:
            venc = ""
        else:
            # Os ICs sobrepõem-se: diferença não distinguível do ruído.
            sobrepoe = (v(l, "tempo_ic_baixo", "plana") <= v(l, "tempo_ic_alto", "hierarquica")
                        and v(l, "tempo_ic_baixo", "hierarquica") <= v(l, "tempo_ic_alto", "plana"))
            venc = (f"plana ({th / tp:.2f}×)" if tp < th else f"hierárquica ({tp / th:.2f}×)")
            if sobrepoe:
                venc = "empate (ICs sobrepostos)"
        print(f"{chave[3]:<22} {partes[0]} {partes[1]}  {venc}")

    print("\nComunicação por repetição (mensagens; KB enviados pelo coordenador)")
    print(f"{'configuração':<22} {'plana':>20} {'hierárquica':>20}")
    for chave, l in t.iterrows():
        partes = []
        for pol in ("plana", "hierarquica"):
            m, b = v(l, "mensagens", pol), v(l, "bytes", pol)
            partes.append(f"{'—':>20}" if m is None else f"{m:9.0f} {b / 1024:8.1f} KB")
        print(f"{chave[3]:<22} {partes[0]:>20} {partes[1]:>20}")


def main():
    categoria = categoria_dos_argumentos()
    df = carregar(categoria)
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' em data/{categoria}/")

    res = resumir(df, categoria)
    if res["t1_s"].isna().any():
        print("Aviso: sem T1 para algumas configurações (falta a base de 1 core).")
    tabelas(res)

    csv = DATA / categoria / "grelha_resumo.csv"
    res.drop(columns=["config"]).to_csv(csv, index=False)
    print(f"\n{len(res)} configurações, {res['repeticoes'].min()}–{res['repeticoes'].max()} "
          f"repetições cada; temperatura máxima {res['temp_max'].max():.1f} °C")
    print(f"Resumo em {csv}")
    print(f"Figura em {figura(res, categoria)}")


if __name__ == "__main__":
    main()
