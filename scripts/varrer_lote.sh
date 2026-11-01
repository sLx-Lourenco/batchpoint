#!/usr/bin/env bash
# Fase 12, parte 2: varre o tamanho do lote da hierárquica para cada tamanho de tarefa.
#
# Uso:   scripts/varrer_lote.sh
#
# Cada tamanho de tarefa tem o mesmo trabalho total por repetição (2 s, ~0,33 s no ideal
# de 6 cores). Só entram os lotes que deixam trabalho para os 3 nós (lote × 3 <= tarefas)
# e que não passam de MAX_LOTES pedidos por repetição (os lotes minúsculos com tarefas de
# 10 µs demorariam minutos por execução e já se sabe que perdem).
# As execuções correm por ordem aleatória, para um efeito lento (temperatura, estado do
# portátil) não favorecer sempre os mesmos lotes. Os resultados vão para data/piloto/,
# com o nome "lote"; a análise é analise/src/lote.py.

set -euo pipefail
cd "$(dirname "$0")/.."

# "iterações tarefas" de cada tamanho (iterações da calibração da torre: 1,6755 ns/iteração)
TAMANHOS=(
    "5968 200000"       # 10 µs
    "59682 20000"       # 100 µs
    "596821 2000"       # 1 ms
    "5968208 200"       # 10 ms
)
LOTES=(1 5 10 25 50 100 250 500 1000 2500 5000 10000)
NOS=3
MAX_LOTES=20000

configs=()
for t in "${TAMANHOS[@]}"; do
    read -r iteracoes tarefas <<< "$t"
    for lote in "${LOTES[@]}"; do
        if (( lote * NOS <= tarefas && tarefas / lote <= MAX_LOTES )); then
            configs+=("$iteracoes $tarefas $lote")
        fi
    done
done
mapfile -t configs < <(printf '%s\n' "${configs[@]}" | shuf)

total=${#configs[@]}
echo "$total execuções, por esta ordem:"
printf '  %s\n' "${configs[@]}"

for k in "${!configs[@]}"; do
    read -r iteracoes tarefas lote <<< "${configs[$k]}"
    echo
    echo "=== [$((k + 1))/$total] iteracoes $iteracoes, tarefas $tarefas, lote $lote ==="
    # A primeira execução compila e envia o jar; as seguintes reaproveitam-no.
    if (( k > 0 )); then export SEM_BUILD=1; fi
    scripts/correr.sh lote hierarquica --lote "$lote" --tarefas "$tarefas" \
        --iteracoes "$iteracoes" --categoria piloto
done

echo
echo "Varredura terminada: $total execuções em data/piloto/"
