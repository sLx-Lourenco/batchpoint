"""Lê os resultados JSON do coordenador para uma tabela do pandas.

Cada ficheiro em data/<categoria>/ é uma execução: a configuração, o ambiente e a lista
das repetições medidas. A tabela devolvida tem uma linha por repetição, com os campos
da configuração repetidos em cada linha, para se poder filtrar e agrupar diretamente.
"""

import json
import sys
from pathlib import Path

import pandas as pd

# Este ficheiro está em analise/src/; a pasta data/ fica na raiz do projeto.
ANALISE = Path(__file__).resolve().parent.parent
DATA = ANALISE.parent / "data"
FIGURAS = ANALISE / "figuras"
CATEGORIAS = ("testes", "piloto", "final")


def categoria_dos_argumentos(omissao: str = "final") -> str:
    """A categoria passada na linha de comando (python x.py piloto), ou a omissão."""
    categoria = sys.argv[1] if len(sys.argv) > 1 else omissao
    if categoria not in CATEGORIAS:
        sys.exit(f"Categoria desconhecida: {categoria} (aceites: {', '.join(CATEGORIAS)})")
    return categoria


def guardar(fig, nome: str, categoria: str) -> Path:
    """Grava a figura em analise/figuras/<categoria>/<nome>.png e .pdf; devolve o PNG."""
    pasta = FIGURAS / categoria
    pasta.mkdir(parents=True, exist_ok=True)
    saida = pasta / f"{nome}.png"
    fig.savefig(saida, dpi=200)
    fig.savefig(saida.with_suffix(".pdf"))
    return saida


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
