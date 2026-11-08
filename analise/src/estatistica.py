"""Testes estatísticos da grelha: plana vs hierárquica em cada configuração.

Uso:   python analise/src/estatistica.py [piloto|final]      (por omissão: final)

Para cada uma das configurações da grelha compara os tempos das duas políticas
(as repetições medidas de cada uma):
  - Shapiro-Wilk em cada amostra: os tempos de parede raramente são normais (caudas,
    patamares), e o teste diz se se pode usar um teste paramétrico;
  - Mann-Whitney U, bilateral: não supõe normalidade; responde "os tempos de uma política
    tendem a ser menores do que os da outra?";
  - correção de Holm sobre todas as comparações (controla o erro de família);
  - tamanho do efeito: Cliff's delta (de -1 a 1; |δ| ≥ 0,474 é "grande") e a razão das
    medianas plana/hierárquica (> 1: a hierárquica é mais rápida).
Grava data/<categoria>/estatistica.csv.
"""

import sys

import numpy as np
import pandas as pd
from scipy import stats

from carregar import DATA, carregar, categoria_dos_argumentos

NOME = "grelha"
ALFA = 0.05
NS_POR_ITERACAO = 1.6755


def cliff_delta(x, y):
    """P(x > y) - P(x < y): positivo quando x tende a ser maior (mais lento) do que y."""
    x, y = np.asarray(x)[:, None], np.asarray(y)[None, :]
    return float((x > y).mean() - (x < y).mean())


def magnitude(d):
    d = abs(d)
    return "desprezável" if d < 0.147 else "pequeno" if d < 0.33 else "médio" if d < 0.474 else "grande"


def holm(p):
    """p-values corrigidos pelo método de Holm (mesma ordem da entrada)."""
    p = np.asarray(p)
    ordem = np.argsort(p)
    m = len(p)
    ajust = np.empty(m)
    acumulado = 0.0
    for k, i in enumerate(ordem):
        acumulado = max(acumulado, min(1.0, (m - k) * p[i]))
        ajust[i] = acumulado
    return ajust


def nome(carga, param, deseq):
    if carga == "mandelbrot":
        return f"Mandelbrot lado {param}"
    s = param * NS_POR_ITERACAO * 1e-9
    t = f"{s * 1e6:.0f} µs" if s < 0.9e-3 else f"{s * 1e3:.0f} ms"
    return f"{t} {deseq}"


def main():
    categoria = categoria_dos_argumentos()
    df = carregar(categoria)
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' em data/{categoria}/")

    linhas = []
    for (carga, param, deseq), g in df.groupby(["carga", "parametro", "desequilibrio"]):
        p = g.loc[g["politica"] == "plana", "tempo_s"].to_numpy()
        h = g.loc[g["politica"] == "hierarquica", "tempo_s"].to_numpy()
        if len(p) == 0 or len(h) == 0:
            continue
        u = stats.mannwhitneyu(p, h, alternative="two-sided")
        linhas.append({
            "carga": carga, "parametro": param, "desequilibrio": deseq,
            "config": nome(carga, param, deseq),
            "n_plana": len(p), "n_hierarquica": len(h),
            "shapiro_p_plana": stats.shapiro(p).pvalue,
            "shapiro_p_hierarquica": stats.shapiro(h).pvalue,
            "mediana_plana": np.median(p), "mediana_hierarquica": np.median(h),
            "razao": np.median(p) / np.median(h),
            "U": u.statistic, "p": u.pvalue,
            "cliff_delta": cliff_delta(p, h),
        })
    res = pd.DataFrame(linhas).sort_values(["carga", "parametro", "desequilibrio"], ascending=[False, True, True])
    res["p_holm"] = holm(res["p"])
    res["magnitude"] = res["cliff_delta"].map(magnitude)

    normais = ((res["shapiro_p_plana"] > ALFA).sum() + (res["shapiro_p_hierarquica"] > ALFA).sum())
    print(f"Shapiro-Wilk: {normais} de {2 * len(res)} amostras compatíveis com normalidade (α = {ALFA})"
          " → teste não paramétrico (Mann-Whitney U)")
    print(f"\n{'configuração':<22} {'razão p/h':>9} {'δ de Cliff':>10} {'magnitude':>12} "
          f"{'p (Holm)':>10}  mais rápida")
    for _, l in res.iterrows():
        mais = "hierárquica" if l["razao"] > 1 else "plana"
        sig = "" if l["p_holm"] < ALFA else "  (não significativo)"
        print(f"{l['config']:<22} {l['razao']:9.3f} {l['cliff_delta']:10.2f} {l['magnitude']:>12} "
              f"{l['p_holm']:10.1e}  {mais}{sig}")

    csv = DATA / categoria / "estatistica.csv"
    res.to_csv(csv, index=False)
    print(f"\nResumo em {csv}")


if __name__ == "__main__":
    main()
