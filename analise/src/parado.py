"""Onde se perde o tempo: ocupado e parado por ligação, nas configurações-chave da grelha.

Uso:   python analise/src/parado.py [piloto|final]      (por omissão: final)

Cada repetição guarda, por ligação, o tempo ocupado (soma das durações das tarefas) e o
parado (threads da ligação × tempo da repetição − ocupado), da fase 11. Na plana cada
ligação é uma thread (6); na hierárquica é um nó de 2 threads (3). Para cada ligação
mostra-se a fração do tempo disponível passada a calcular (mediana das repetições).

As ligações são numeradas pela ordem em que se ligaram, não pelo id do nó, e quem apanha
o trabalho pesado muda de repetição para repetição; por isso, em cada repetição, ordenam-se
da mais ocupada para a menos ocupada antes de tirar a mediana.
"""

import sys

import matplotlib.pyplot as plt
import numpy as np

from carregar import DATA, carregar, categoria_dos_argumentos, guardar

NOME = "grelha"
# (carga, parâmetro, desequilíbrio, título): uma de cada regime da grelha
CHAVE = [
    ("sintetica", 5968, "uniforme", "Sintética 10 µs: a rede domina"),
    ("sintetica", 5968208, "baralhada", "Sintética 10 ms, baralhada: a cauda"),
    ("sintetica", 5968208, "agrupada_fim", "Sintética 10 ms, agrupada no fim: lotes pesados no fim"),
    ("mandelbrot", 512, "natural", "Mandelbrot lado 512: poucas tarefas desiguais"),
]

OCUPADO = "#2a78d6"
PARADO = "#d7e6f8"
TEXTO = "#0b0b0b"
TEXTO_2 = "#52514e"
GRELHA = "#e4e3df"


def fracoes(g):
    """Fração ocupada por posição: em cada repetição ordenam-se as ligações da mais à menos
    ocupada (a ordem das ligações muda de repetição para repetição); depois, a mediana de
    cada posição. Assim "nó 1" é sempre o mais carregado dessa repetição."""
    oc = np.array(g["ocupado_ns"].tolist(), dtype=float)
    pa = np.array(g["parado_ns"].tolist(), dtype=float)
    f = -np.sort(-(oc / (oc + np.clip(pa, 0, None))), axis=1)
    return np.median(f, axis=0)


def main():
    categoria = categoria_dos_argumentos()
    df = carregar(categoria)
    df = df[df["ficheiro"].str.endswith(f"_{NOME}.json")]
    if df.empty:
        sys.exit(f"Nenhuma execução '{NOME}' em data/{categoria}/")

    fig, eixos = plt.subplots(len(CHAVE), 2, figsize=(8, 1.55 * len(CHAVE) + 0.8),
                              sharex=True, squeeze=False)
    linhas_csv = []
    print("Fração do tempo a calcular, por ligação (mediana das repetições)")
    for i, (carga, param, deseq, titulo) in enumerate(CHAVE):
        for j, pol in enumerate(("plana", "hierarquica")):
            ax = eixos[i, j]
            g = df[(df["carga"] == carga) & (df["parametro"] == param)
                   & (df["desequilibrio"] == deseq) & (df["politica"] == pol)]
            if g.empty:
                ax.set_visible(False)
                continue
            f = fracoes(g)
            y = np.arange(len(f))
            ax.barh(y, f, color=OCUPADO, height=0.7, zorder=2)
            ax.barh(y, 1 - f, left=f, color=PARADO, height=0.7, zorder=2)
            ax.set_yticks(y, [("thread " if pol == "plana" else "nó ") + str(k + 1) for k in y],
                          fontsize=7, color=TEXTO_2)
            ax.invert_yaxis()
            ax.set_xlim(0, 1)
            ax.tick_params(colors=TEXTO_2, labelsize=8, length=0)
            for lado in ax.spines.values():
                lado.set_visible(False)
            ax.grid(axis="x", color=GRELHA, lw=0.8, zorder=0)
            media = f.mean()
            ax.set_title(f"{pol}: {media:.0%} a calcular", loc="left", fontsize=9, color=TEXTO)
            if j == 0:
                ax.annotate(titulo, xy=(0, 1.42), xycoords="axes fraction", fontsize=10,
                            color=TEXTO, fontweight="bold")
            print(f"  {titulo[:34]:<34} {pol:<11} " + " ".join(f"{x:4.0%}" for x in f))
            linhas_csv.append(f"{carga},{param},{deseq},{pol}," + ",".join(f"{x:.4f}" for x in f))

    for ax in eixos[-1]:
        ax.set_xlabel("fração do tempo da repetição", color=TEXTO_2, fontsize=9)
        ax.xaxis.set_major_formatter(plt.FuncFormatter(lambda v, _: f"{v:.0%}"))
    eixos[0, 1].legend(["a calcular", "parado"], frameon=False, fontsize=8,
                       loc="lower right", bbox_to_anchor=(1, 1.25), ncol=2)
    fig.tight_layout(h_pad=2.2)

    csv = DATA / categoria / "parado_resumo.csv"
    csv.write_text("carga,parametro,desequilibrio,politica,fracoes_ocupadas\n" + "\n".join(linhas_csv) + "\n")
    print(f"Resumo em {csv}")
    print(f"Figura em {guardar(fig, 'parado', categoria)}")


if __name__ == "__main__":
    main()
