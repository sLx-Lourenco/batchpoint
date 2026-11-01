#!/usr/bin/env bash
# Corre um comando com o sono profundo do CPU do portátil desativado e repõe tudo no fim.
#
# Uso:   scripts/sono_limitado.sh <comando> [argumentos...]
# Ex.:   scripts/sono_limitado.sh scripts/grelha.sh final
#
# O coordenador corre no portátil. Entre mensagens, o CPU entra em estados de poupança
# (C2: 127 µs para acordar, C3: 1048 µs) e cada mensagem seguinte espera que ele acorde.
# Isto só prejudica a política plana, que troca muitas mensagens (teste A/B em
# scripts/teste_cstates.sh). Aqui só ficam os estados com latência <= 2 µs, como na torre
# (fase 8). O estado anterior de cada core é guardado e reposto no fim, mesmo com erro
# ou Ctrl+C.

set -euo pipefail
cd "$(dirname "$0")/.."

if [ $# -lt 1 ]; then
    echo "Uso: $0 <comando> [argumentos...]" >&2
    exit 1
fi

if ! cpupower idle-info > /dev/null 2>&1; then
    echo "Falta o cpupower: sudo apt-get install linux-tools-common linux-tools-\$(uname -r)" >&2
    exit 1
fi

sudo -v     # pede a palavra-passe já, para não parar a meio

# Mantém o sudo válido durante execuções longas (a grelha final passa os 15 minutos).
while true; do sudo -n -v; sleep 60; done 2> /dev/null &
MANTER_SUDO=$!

ESTADO=$(mktemp)
for f in /sys/devices/system/cpu/cpu*/cpuidle/state*/disable; do
    echo "$f $(cat "$f")"
done > "$ESTADO"

repor() {
    kill "$MANTER_SUDO" 2> /dev/null || true
    echo
    echo "A repor os estados de sono do portátil..."
    while read -r f v; do
        echo "$v" | sudo tee "$f" > /dev/null
    done < "$ESTADO"
    rm -f "$ESTADO"
    echo "Reposto."
}
trap repor EXIT

sudo cpupower idle-set -D 2 > /dev/null
echo "Sono do portátil limitado aos estados com latência <= 2 µs."
echo

"$@"
