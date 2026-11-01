#!/usr/bin/env bash
# Fase 12: testa se o sono do CPU do portátil (C-states) atrasa a política plana.
#
# Uso:   scripts/teste_cstates.sh
#
# Hipótese: com tarefas de 1 ms, o coordenador fica ~1 ms parado entre mensagens, o CPU do
# portátil entra num estado de poupança profundo e cada "acordar" custa centenas de µs.
# Teste A/B na mesma sessão, para a mesma configuração da plana:
#   - "normal":   com os estados de sono como estão;
#   - "limitado": só com os estados de latência <= 2 µs (o mesmo que na torre, fase 8).
# Corre 100 µs e 1 ms, alternando normal/limitado. No fim (mesmo com erro ou Ctrl+C) repõe
# exatamente o estado de sono que o portátil tinha antes.
#
# Resultados em data/piloto/ (nomes cstates_normal e cstates_limitado); os estados do
# portátil ficam em data/rede/cstates_portatil.txt.

set -euo pipefail
cd "$(dirname "$0")/.."

# 1. O cpupower (no Ubuntu vem no linux-tools da versão do kernel).
if ! cpupower idle-info > /dev/null 2>&1; then
    echo "A instalar o cpupower..."
    sudo apt-get install -y linux-tools-common "linux-tools-$(uname -r)"
fi

sudo -v     # pede a palavra-passe já, para não parar a meio

# 2. Guardar o estado atual (que estados estão desativados em cada core), para repor no fim.
ESTADO=$(mktemp)
for f in /sys/devices/system/cpu/cpu*/cpuidle/state*/disable; do
    echo "$f $(cat "$f")"
done > "$ESTADO"

repor() {
    echo
    echo "A repor os estados de sono do portátil..."
    while read -r f v; do
        echo "$v" | sudo tee "$f" > /dev/null
    done < "$ESTADO"
    rm -f "$ESTADO"
    echo "Reposto."
}
trap repor EXIT

mkdir -p data/rede
cpupower idle-info | tee data/rede/cstates_portatil.txt

# 3. O teste A/B.
CONFIGS=(
    "--tarefas 20000 --iteracoes 59682"     # 100 µs
    "--tarefas 2000 --iteracoes 596821"     # 1 ms
)
primeira=1
for c in "${CONFIGS[@]}"; do
    for modo in normal limitado; do
        if [ "$modo" = limitado ]; then
            sudo cpupower idle-set -D 2 > /dev/null
        else
            while read -r f v; do echo "$v" | sudo tee "$f" > /dev/null; done < "$ESTADO"
        fi
        if (( primeira == 0 )); then export SEM_BUILD=1; fi
        primeira=0
        echo
        echo "=== plana $c, sono $modo ==="
        # shellcheck disable=SC2086
        scripts/correr.sh "cstates_$modo" plana $c --repeticoes 10 --categoria piloto
    done
done

# 4. Resumo: mediana do tempo de cada execução deste teste.
echo
echo "Resumo (mediana do tempo):"
ls -t data/piloto/*_cstates_*.json | head -4 | sort | python3 -c '
import json, statistics, sys
for nome in sys.stdin.read().split():
    d = json.load(open(nome))
    p = d["configuracao"]["parametro"]
    t = statistics.median(r["tempo_ns"] for r in d["repeticoes"]) / 1e9
    modo = "limitado" if "limitado" in nome else "normal"
    print(f"  {p:>7} iteracoes, sono {modo:<8}: {t:.3f} s")
'
