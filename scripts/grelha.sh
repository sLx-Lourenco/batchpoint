#!/usr/bin/env bash
# Fase 12, parte 5: corre a grelha de experiências, por ordem aleatória.
#
# Uso:   scripts/grelha.sh piloto     (10 repetições medidas, com verificação do checksum)
#        scripts/grelha.sh final      (30 repetições medidas, sem verificação)
#
# A grelha:
#   - sintética: política (plana, hierárquica) × tamanho (10 µs, 100 µs, 1 ms, 10 ms)
#                × desequilíbrio (uniforme, agrupada, baralhada)          = 24 configurações
#   - Mandelbrot: política × lado (512, 128, 32, 8)                        =  8 configurações
# Cada tamanho da sintética tem 2 s de trabalho por repetição. Na hierárquica, o lote é
# tarefas / 20 (escolhido em analise/src/lote.py). O aquecimento é o valor por omissão
# do Config (10, escolhido em analise/src/aquecimento.py).
#
# A ordem é baralhada para que um efeito lento (temperatura, estado do portátil) não
# favoreça sempre a mesma política. Resultados em data/<categoria>/, com o nome "grelha".
# Análise: analise/src/grelha.py <categoria>.

set -euo pipefail
cd "$(dirname "$0")/.."

CATEGORIA=${1:-}
case "$CATEGORIA" in
    piloto) EXTRA=(--repeticoes 10 --verificar y) ;;
    final)  EXTRA=(--repeticoes 30) ;;
    *) echo "Uso: $0 <piloto|final>" >&2; exit 1 ;;
esac

# "argumentos da carga|tarefas" (as tarefas servem para calcular o lote)
SINTETICA=(
    "--tarefas 200000 --iteracoes 5968|200000"       # 10 µs
    "--tarefas 20000 --iteracoes 59682|20000"        # 100 µs
    "--tarefas 2000 --iteracoes 596821|2000"         # 1 ms
    "--tarefas 200 --iteracoes 5968208|200"          # 10 ms
)
DESEQUILIBRIOS=(uniforme agrupada baralhada)
MANDELBROT=(
    "--carga mandelbrot --lado 512|64"
    "--carga mandelbrot --lado 128|1024"
    "--carga mandelbrot --lado 32|16384"
    "--carga mandelbrot --lado 8|262144"
)

configs=()
for politica in plana hierarquica; do
    for s in "${SINTETICA[@]}"; do
        for d in "${DESEQUILIBRIOS[@]}"; do
            configs+=("$politica|${s%|*} --desequilibrio $d|${s#*|}")
        done
    done
    for m in "${MANDELBROT[@]}"; do
        configs+=("$politica|$m")
    done
done
mapfile -t configs < <(printf '%s\n' "${configs[@]}" | shuf)

total=${#configs[@]}
echo "Grelha '$CATEGORIA': $total execuções, por ordem aleatória."
inicio=$(date +%s)

for k in "${!configs[@]}"; do
    IFS='|' read -r politica args tarefas <<< "${configs[$k]}"
    if [ "$politica" = hierarquica ]; then
        lote=$(( tarefas / 20 > 0 ? tarefas / 20 : 1 ))
    else
        lote=1
    fi
    decorrido=$(( $(date +%s) - inicio ))
    echo
    echo "=== [$((k + 1))/$total] $politica $args --lote $lote   ($((decorrido / 60)) min) ==="
    # A primeira execução compila e envia o jar; as seguintes reaproveitam-no.
    if (( k > 0 )); then export SEM_BUILD=1; fi
    # shellcheck disable=SC2086
    scripts/correr.sh grelha "$politica" $args --lote "$lote" \
        --categoria "$CATEGORIA" "${EXTRA[@]}"
done

echo
echo "Grelha '$CATEGORIA' terminada: $total execuções em $(( ($(date +%s) - inicio) / 60 )) min."
