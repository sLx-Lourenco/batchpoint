#!/usr/bin/env bash
# Fase 12, partes 3 e 4: as linhas de base e a comparação entre os pares de cores.
#
# Uso:   scripts/linhas_base.sh
#
# Parte 3 (nome "base1"): cada carga com 1 nó de 1 thread (core 0). Dá o T1 do speedup.
#   Corre pelo mesmo caminho das experiências (Docker, rede, coordenador), na hierárquica
#   com lote = tarefas / 20, para ter o mínimo de comunicação.
# Parte 4 (nome "nos"): o mesmo trabalho com 1 nó de 2 threads em 0-1, depois 2-3 e 4-5.
#   Os três tempos devem coincidir; se um par for mais lento, enviesava as experiências.
#
# O cálculo sequencial é estável, por isso a base de 1 core usa menos repetições.
# Resultados em data/piloto/; análise em analise/src/linhas_base.py.

set -euo pipefail
cd "$(dirname "$0")/.."

# "argumentos da carga" e o lote (tarefas / 20) de cada configuração
CARGAS=(
    "--tarefas 200000 --iteracoes 5968|10000"            # 10 µs
    "--tarefas 20000 --iteracoes 59682|1000"             # 100 µs
    "--tarefas 2000 --iteracoes 596821|100"              # 1 ms
    "--tarefas 200 --iteracoes 5968208|10"               # 10 ms
    "--carga mandelbrot --lado 512|3"                    # 64 tarefas
    "--carga mandelbrot --lado 128|51"                   # 1024 tarefas
    "--carga mandelbrot --lado 32|819"                   # 16 384 tarefas
    "--carga mandelbrot --lado 8|13107"                  # 262 144 tarefas
)
BASE_REPETICOES="--aquecimento 2 --repeticoes 5"

primeira=1
correr() {
    # A primeira execução compila e envia o jar; as seguintes reaproveitam-no.
    if (( primeira == 0 )); then export SEM_BUILD=1; fi
    primeira=0
    scripts/correr.sh "$@"
}

echo "=== Parte 3: 1 nó, 1 thread (core 0) ==="
for c in "${CARGAS[@]}"; do
    args=${c%|*}
    lote=${c#*|}
    echo "--- base1: $args, lote $lote"
    # shellcheck disable=SC2086
    NOS=1 THREADS=1 PRIMEIRO_CORE=0 correr base1 hierarquica $args --lote "$lote" \
        $BASE_REPETICOES --categoria piloto
done

echo
echo "=== Parte 4: 1 nó de 2 threads em cada par de cores ==="
for core in 0 2 4; do
    echo "--- nos: cores $core-$((core + 1))"
    NOS=1 THREADS=2 PRIMEIRO_CORE=$core correr "nos$core" hierarquica \
        --tarefas 2000 --iteracoes 596821 --lote 100 --categoria piloto
done

echo
echo "Linhas de base terminadas (data/piloto/, nomes base1 e nos0/nos2/nos4)."
