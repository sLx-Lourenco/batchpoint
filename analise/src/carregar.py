"""Lê os resultados JSON do coordenador para uma tabela do pandas.

Cada ficheiro em data/<categoria>/ é uma execução: a configuração, o ambiente e a lista
das repetições medidas. A tabela devolvida tem uma linha por repetição, com os campos
da configuração repetidos em cada linha, para se poder filtrar e agrupar diretamente.
"""

import json
from pathlib import Path

import pandas as pd

# Este ficheiro está em analise/src/; a pasta data/ fica na raiz do projeto.
ANALISE = Path(__file__).resolve().parent.parent
DATA = ANALISE.parent / "data"
FIGURAS = ANALISE / "figuras"


def carregar(categoria: str) -> pd.DataFrame:
    """Uma linha por repetição de todas as execuções de data/<categoria>/."""
    pasta = DATA / categoria
    linhas = []
    for ficheiro in sorted(pasta.glob("*.json")):
        with open(ficheiro, encoding="utf-8") as f:
            dados = json.load(f)
        for rep in dados["repeticoes"]:
            linhas.append({
                **dados["configuracao"],
                **rep,
                "ficheiro": ficheiro.name,
                "data": dados["data"],
            })

    if not linhas:
        raise FileNotFoundError(f"Nenhum resultado em {pasta}")

    df = pd.DataFrame(linhas)
    df["tempo_s"] = df["tempo_ns"] / 1e9
    df["rotulo"] = df.apply(rotulo, axis=1)
    return df


def rotulo(linha) -> str:
    """Nome curto de uma configuração, para títulos e legendas."""
    if linha["carga"] == "mandelbrot":
        texto = f"{linha['politica']}, Mandelbrot lado {linha['parametro']}"
    else:
        texto = f"{linha['politica']}, sintética {linha['parametro']} it., {linha['desequilibrio']}"
    if linha["politica"] == "hierarquica":
        texto += f", lote {linha['lote']}"
    return texto


if __name__ == "__main__":
    df = carregar("piloto")
    print(df[["rotulo", "repeticao", "tempo_s"]])
    print(f"\n{df['ficheiro'].nunique()} ficheiros, {len(df)} repetições")
