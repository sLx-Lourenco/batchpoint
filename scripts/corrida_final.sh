#!/usr/bin/env bash
# Corrida completa dos resultados: todos os estudos, numa só sessão, nas condições finais.
# Cada execução individual é feita pelo scripts/correr.sh.
#
# Uso:   scripts/corrida_final.sh
#
# Antes: portátil à corrente, com a tampa aberta. O script põe o portátil no perfil de
# energia performance e repõe o perfil anterior no fim. Pede a palavra-passe do sudo uma vez
# (para limitar o sono do CPU do portátil a latência <= 2 µs, como a torre).
#
# Etapas (~3 h):
#   1. ambiente: portátil e torre (versões, frequência, cores, estados de sono)
#   2. aquecimento: 4 configurações, 0 + 50 repetições                       ~8 min
#   3. lote: hierárquica, lotes de 1 a 10 000, por ordem aleatória           ~25 min
#   4. linhas de base: 1 core por carga; 1 nó em cada par de cores           ~15 min
#   5. caracterização do Mandelbrot na torre, num core (custo de cada bloco)  ~1 min
#   6. grelha: 64 configurações, por ordem aleatória                         ~2 h
#   7. análises (analise/src/*.py final)
# O perfil de energia e o sono do portátil são repostos no fim, mesmo com erro ou Ctrl+C.
#
# Os dados vão para data/final/ (os que lá estiverem são arquivados em data/final_<data>/,
# não apagados). No fim, fica tudo também em resultados-finais/:
#   dados/  dados/medicoes/  figuras/  resultados.txt  ambiente.txt  execucao.log
#
# Decisões desta corrida:
#   aquecimento = 10 repetições (o valor por omissão do Config);
#   lote da hierárquica = ⌈tarefas / 24⌉: 24 lotes por repetição, 8 por nó (com 20 lotes,
#     um nó ficava com 7 e outro com 6, e a hierárquica nunca passava de 0,35 s);
#   sintética: 7 tamanhos, 2,4 s de trabalho por repetição (100 ms: 12 s, 20 repetições);
#   desequilíbrios: uniforme, agrupada (longas no início), agrupada_fim, baralhada.

set -euo pipefail
cd "$(dirname "$0")/.."

# Impede o portátil de suspender (por inatividade ou por ação do sistema) enquanto corre.
# Fechar a tampa continua a poder suspendê-lo: deixa-a aberta.
if [ -z "${SEM_SUSPENSAO:-}" ] && command -v systemd-inhibit > /dev/null; then
    exec env SEM_SUSPENSAO=1 systemd-inhibit --what=idle:sleep --who=batchpoint \
        --why="corrida das experiências" "$0" "$@"
fi

DESTINO=resultados-finais
CATEGORIA=final
PY=analise/.venv/bin/python
TORRE=fixo
SONO_SYSFS=${SONO_SYSFS:-/sys/devices/system/cpu}

# "argumentos da carga|tarefas|repetições" de cada carga da grelha (as tarefas dão o lote)
SINTETICA=(
    "--tarefas 240000 --iteracoes 5968|240000|30"       # 10 µs
    "--tarefas 24000 --iteracoes 59682|24000|30"        # 100 µs
    "--tarefas 2400 --iteracoes 596821|2400|30"         # 1 ms
    "--tarefas 1200 --iteracoes 1193642|1200|30"        # 2 ms
    "--tarefas 480 --iteracoes 2984104|480|30"          # 5 ms
    "--tarefas 240 --iteracoes 5968208|240|30"          # 10 ms
    "--tarefas 120 --iteracoes 59682080|120|20"         # 100 ms (12 s por repetição)
)
# Os 4 tamanhos do estudo do lote (os outros seguem a mesma regra).
SINTETICA_LOTE=(
    "--tarefas 240000 --iteracoes 5968|240000"
    "--tarefas 24000 --iteracoes 59682|24000"
    "--tarefas 2400 --iteracoes 596821|2400"
    "--tarefas 240 --iteracoes 5968208|240"
)
MANDELBROT=(
    "--carga mandelbrot --lado 512|64|30"
    "--carga mandelbrot --lado 128|1024|30"
    "--carga mandelbrot --lado 32|16384|30"
    "--carga mandelbrot --lado 8|262144|30"
)
DESEQUILIBRIOS=(uniforme agrupada agrupada_fim baralhada)
GERADOR=src/main/java/pt/ulisboa/batchpoint/cargas/GeradorDeTarefas.java

# ---------------------------------------------------------------------------------------
# Auxiliares

etapa() { echo; echo "######## $1 ($(date +%H:%M)) ########"; }
lote_de() { local t=$1; echo $(( (t + 23) / 24 )); }     # ⌈tarefas / 24⌉

# Uma execução. A primeira compila e envia o jar; as seguintes reaproveitam-no.
primeira=1
correr() {
    if (( primeira == 0 )); then export SEM_BUILD=1; fi
    primeira=0
    scripts/correr.sh "$@" --categoria "$CATEGORIA"
}

# O sono do CPU do portátil. O coordenador corre aqui: entre mensagens espaçadas, o CPU
# entra em estados que demoram 127 µs (C2) ou 1048 µs (C3) a acordar, o que só prejudica
# a plana. Guarda-se o estado de cada core para o repor exatamente no fim.
ESTADO_SONO=$(mktemp)
guardar_sono() {
    for f in "$SONO_SYSFS"/cpu*/cpuidle/state*/disable; do echo "$f $(cat "$f")"; done > "$ESTADO_SONO"
}
repor_sono() {
    while read -r f v; do echo "$v" | sudo tee "$f" > /dev/null; done < "$ESTADO_SONO"
}
limitar_sono() { sudo cpupower idle-set -D 2 > /dev/null; }

MANTER_SUDO=""
PERFIL_ANTERIOR=""
terminar() {
    [ -n "$MANTER_SUDO" ] && kill "$MANTER_SUDO" 2> /dev/null || true
    if [ -n "$PERFIL_ANTERIOR" ] && [ "$PERFIL_ANTERIOR" != performance ]; then
        powerprofilesctl set "$PERFIL_ANTERIOR" && echo "Perfil de energia reposto: $PERFIL_ANTERIOR"
    fi
    if [ -s "$ESTADO_SONO" ]; then
        echo; echo "A repor o sono do CPU do portátil..."
        repor_sono && echo "Reposto."
    fi
    rm -f "$ESTADO_SONO"
}

# ---------------------------------------------------------------------------------------
# Verificações, antes de gastar tempo

if [ -e "$DESTINO" ]; then
    echo "Já existe $DESTINO/. Muda-lhe o nome ou apaga-o antes de repetir." >&2; exit 1
fi
[ -x "$PY" ] || { echo "Falta o ambiente Python em analise/.venv/" >&2; exit 1; }
"$PY" -c "import scipy" 2> /dev/null || { echo "Falta o scipy: $PY -m pip install scipy" >&2; exit 1; }
grep -q '"agrupada_fim"' "$GERADOR" || { echo "O $GERADOR não tem o desequilíbrio agrupada_fim" >&2; exit 1; }
command -v powerprofilesctl > /dev/null || { echo "Falta o powerprofilesctl" >&2; exit 1; }

ssh -o ConnectTimeout=5 "$TORRE" true || { echo "A torre ($TORRE) não responde" >&2; exit 1; }
if ! cpupower idle-info > /dev/null 2>&1; then
    echo "A instalar o cpupower..."
    sudo apt-get install -y linux-tools-common "linux-tools-$(uname -r)"
fi

sudo -v
while true; do sudo -n -v; sleep 60; done 2> /dev/null &
MANTER_SUDO=$!
guardar_sono
PERFIL_ANTERIOR=$(powerprofilesctl get)
trap terminar EXIT

# O coordenador corre no portátil: no modo equilibrado, baixa a frequência e atrasa a plana.
powerprofilesctl set performance
if [ "$(powerprofilesctl get)" != performance ]; then
    echo "Não foi possível pôr o portátil no perfil performance" >&2; exit 1
fi

mkdir -p "$DESTINO"
exec > >(tee -a "$DESTINO/execucao.log") 2>&1

# ---------------------------------------------------------------------------------------
etapa "1/7 ambiente"

if [ -d "data/$CATEGORIA" ] && [ -n "$(ls -A "data/$CATEGORIA")" ]; then
    arquivo="data/${CATEGORIA}_$(date +%Y-%m-%d_%H%M%S)"
    mv "data/$CATEGORIA" "$arquivo"
    echo "Dados anteriores de data/$CATEGORIA/ arquivados em $arquivo/"
fi
mkdir -p "data/$CATEGORIA"

{
    echo "inicio: $(date -Iseconds)"
    echo "commit: $(git rev-parse HEAD 2> /dev/null || echo '?')"
    echo
    echo "== portátil (coordenador) =="
    echo "kernel: $(uname -r)"
    lscpu | grep -E 'Model name|^CPU\(s\)' || true
    echo "perfil de energia: $(powerprofilesctl get)"
    java -version 2>&1 | head -1
    echo "python: $("$PY" --version 2>&1)"
    echo "estados de sono (durante a corrida ficam só os de latência <= 2 us):"
    cpupower idle-info 2> /dev/null | grep -E '^[A-Z0-9_]+:$|Latency' | paste - - | sed 's/^/  /' || true
    echo
    echo "== torre (nós) =="
    ssh "$TORRE" '
        echo "kernel: $(uname -r)"
        lsb_release -ds 2> /dev/null || true
        docker --version
        lscpu | grep -E "Model name|^CPU\(s\)|Thread\(s\) per core"
        echo "SMT ativo: $(cat /sys/devices/system/cpu/smt/active 2> /dev/null)"
        echo "boost: $(cat /sys/devices/system/cpu/cpufreq/boost 2> /dev/null)"
        echo "governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)"
        echo "frequência (kHz): $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq)"
        echo "batchpoint.slice: $(systemctl show batchpoint.slice -p AllowedCPUs --value)"
        echo "system.slice: $(systemctl show system.slice -p AllowedCPUs --value)"
        echo "estados de sono do core 0:"
        for s in /sys/devices/system/cpu/cpu0/cpuidle/state*; do
            echo "  $(cat $s/name): latência $(cat $s/latency) us, desativado=$(cat $s/disable)"
        done
    ' || echo "(não foi possível ler o ambiente da torre)"
} > "$DESTINO/ambiente.txt"
cat "$DESTINO/ambiente.txt"

# ---------------------------------------------------------------------------------------
# Sempre com o sono limitado (reposto no fim pelo trap).
limitar_sono
echo; echo "Sono do portátil limitado aos estados com latência <= 2 µs."

# ---------------------------------------------------------------------------------------
etapa "2/7 aquecimento"
# 0 descartadas + 50 medidas: o JSON guarda todas, incluindo as primeiras.
for c in "plana --tarefas 24000 --iteracoes 5968" \
         "hierarquica --lote 1000 --tarefas 24000 --iteracoes 5968" \
         "plana --carga mandelbrot --lado 128" \
         "hierarquica --carga mandelbrot --lado 128 --lote 43"; do
    echo "--- aquecimento: $c"
    # shellcheck disable=SC2086
    correr aquecimento $c --aquecimento 0 --repeticoes 50
done

# ---------------------------------------------------------------------------------------
etapa "3/7 lote"
# Só os lotes que deixam trabalho aos 3 nós e não passam de 20 000 pedidos por repetição.
configs=()
for s in "${SINTETICA_LOTE[@]}"; do
    tarefas=${s#*|}
    for lote in 1 5 10 25 50 100 250 500 1000 2500 5000 10000 "$(lote_de "$tarefas")"; do
        if (( lote * 3 <= tarefas && tarefas / lote <= 20000 )); then
            configs+=("${s%|*}|$lote")
        fi
    done
done
mapfile -t configs < <(printf '%s\n' "${configs[@]}" | sort -u | shuf)
for k in "${!configs[@]}"; do
    IFS='|' read -r args lote <<< "${configs[$k]}"
    echo "--- [$((k + 1))/${#configs[@]}] lote $lote, $args"
    # shellcheck disable=SC2086
    correr lote hierarquica $args --lote "$lote"
done

# ---------------------------------------------------------------------------------------
etapa "4/7 linhas de base"
# T1 do speedup: cada carga com 1 nó de 1 thread (core 0), na hierárquica com o lote da regra.
for s in "${SINTETICA[@]}" "${MANDELBROT[@]}"; do
    IFS='|' read -r args tarefas _ <<< "$s"
    lote=$(lote_de "$tarefas")
    echo "--- base1: $args, lote $lote"
    # shellcheck disable=SC2086
    NOS=1 THREADS=1 PRIMEIRO_CORE=0 correr base1 hierarquica $args --lote "$lote" \
        --aquecimento 2 --repeticoes 10
done
# Os 3 pares de cores: o mesmo trabalho com 1 nó de 2 threads em 0-1, 2-3 e 4-5.
for core in 0 2 4; do
    echo "--- nos: cores $core-$((core + 1))"
    NOS=1 THREADS=2 PRIMEIRO_CORE=$core correr "nos$core" hierarquica \
        --tarefas 2400 --iteracoes 596821 --lote 100
done

# ---------------------------------------------------------------------------------------
etapa "5/7 caracterização do Mandelbrot"
# Na torre, sem contentores, presa ao core 2 (um dos cores dos nós). Mede cada bloco de cada
# lado e grava o CSV que o analise/src/mandelbrot_mapa.py lê. Usa o jar já enviado.
mkdir -p data/caracterizacao
ssh "$TORRE" "taskset -c 2 java -cp ~/batchpoint/batchpoint.jar \
    pt.ulisboa.batchpoint.ferramentas.CaracterizacaoMandelbrot --pasta ~/batchpoint/caracterizacao" \
    | tee data/caracterizacao/mandelbrot_torre.txt
scp -q "$TORRE":~/batchpoint/caracterizacao/mandelbrot_blocos.csv data/caracterizacao/

# ---------------------------------------------------------------------------------------
etapa "6/7 grelha"
configs=()
for politica in plana hierarquica; do
    for s in "${SINTETICA[@]}"; do
        for d in "${DESEQUILIBRIOS[@]}"; do
            IFS='|' read -r args tarefas reps <<< "$s"
            configs+=("$politica|$args --desequilibrio $d|$tarefas|$reps")
        done
    done
    for m in "${MANDELBROT[@]}"; do configs+=("$politica|$m"); done
done
mapfile -t configs < <(printf '%s\n' "${configs[@]}" | shuf)
for k in "${!configs[@]}"; do
    IFS='|' read -r politica args tarefas reps <<< "${configs[$k]}"
    if [ "$politica" = hierarquica ]; then lote=$(lote_de "$tarefas"); else lote=1; fi
    echo "--- [$((k + 1))/${#configs[@]}] $politica $args --lote $lote, $reps repetições"
    # shellcheck disable=SC2086
    correr grelha "$politica" $args --lote "$lote" --repeticoes "$reps" --verificar y
done

# ---------------------------------------------------------------------------------------
etapa "7/7 análises"
# Primeiro a cópia dos dados: uma análise que falhe não pode deixar os dados por guardar.
mkdir -p "$DESTINO/dados/medicoes" "$DESTINO/figuras"
cp "data/$CATEGORIA"/*.json "$DESTINO/dados/"
for d in calibracao caracterizacao rede; do
    if [ -d "data/$d" ]; then cp -r "data/$d" "$DESTINO/dados/medicoes/"; fi
done

# Cada análise corre à parte: se uma falhar, as outras continuam (e o erro fica registado).
{
    for s in aquecimento lote linhas_base grelha estatistica cruzamento parado; do
        echo "==================== $s ===================="
        "$PY" "analise/src/$s.py" "$CATEGORIA" || echo "!! a análise $s falhou"
        echo
    done
    echo "==================== mandelbrot_mapa ===================="
    "$PY" analise/src/mandelbrot_mapa.py || echo "!! a análise mandelbrot_mapa falhou"
} 2>&1 | tee "$DESTINO/resultados.txt"

# Os resumos CSV que as análises gravaram, e as figuras.
cp "data/$CATEGORIA"/*.csv "$DESTINO/dados/" 2> /dev/null || true
cp "analise/figuras/$CATEGORIA"/* analise/figuras/caracterizacao/* "$DESTINO/figuras/" 2> /dev/null || true
echo "fim: $(date -Iseconds)" >> "$DESTINO/ambiente.txt"

etapa "terminado"
echo "Tudo em $DESTINO/ ($(ls "$DESTINO/dados" | wc -l) ficheiros de dados," \
     "$(ls "$DESTINO/figuras" | wc -l) figuras)."
