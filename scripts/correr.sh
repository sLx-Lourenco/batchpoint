#!/usr/bin/env bash
# Corre uma experiência: o coordenador no portátil e os 3 nós em contentores Docker na torre.
#
# Uso:   scripts/correr.sh <nome> <plana|hierarquica> <argumentos do coordenador...>
#
# Exemplos:
#   scripts/correr.sh cabo plana --tarefas 20000 --iteracoes 5972 --desequilibrio baralhada --verificar y
#   scripts/correr.sh cabo hierarquica --lote 100 --tarefas 20000 --iteracoes 5972 --categoria piloto
#
# O script trata do resto:
#   - número de ligações: 6 na plana (uma por thread), 3 na hierárquica (uma por nó);
#   - a carga (--carga) e a porta (--porta) passam também aos nós;
#   - o <nome> vai para o nome do ficheiro JSON (--nome).
# Se um nó falhar, a saída dele fica disponível com:  ssh fixo docker logs no1

set -euo pipefail

# O coordenador corre no portátil: no modo equilibrado, baixa a frequência e atrasa a plana.
if command -v powerprofilesctl > /dev/null && [ "$(powerprofilesctl get)" != performance ]; then
    echo "ERRO: o portátil está no perfil '$(powerprofilesctl get)'; corre: powerprofilesctl set performance" >&2
    exit 1
fi

cd "$(dirname "$0")/.."                 # corre sempre a partir da raiz do projeto

if [ $# -lt 2 ]; then
    echo "Uso: $0 <nome> <plana|hierarquica> <argumentos do coordenador...>" >&2
    exit 1
fi
NOME=$1
POLITICA=$2
shift 2

TORRE=fixo                  # nome da torre no ~/.ssh/config
COORD_IP=10.42.0.1          # IP do portátil no cabo direto
NOS=${NOS:-3}                   # pode ser mudado de fora: NOS=1 scripts/correr.sh ...
THREADS=${THREADS:-2}
PRIMEIRO_CORE=${PRIMEIRO_CORE:-0}
IMAGEM=batchpoint-no
SLICE=batchpoint.slice      # slice com os cores 0-5 (ver setup_torre.md)

# A carga e a porta têm de ser as mesmas no coordenador e nos nós: procura-as nos argumentos.
CARGA=sintetica
PORTA=9000
LOTE=1
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]} - 1; i++)); do
    case "${ARGS[$i]}" in
        --carga) CARGA="${ARGS[$((i + 1))]}" ;;
        --porta) PORTA="${ARGS[$((i + 1))]}" ;;
        --lote)  LOTE="${ARGS[$((i + 1))]}" ;;
    esac
done

case "$POLITICA" in
    plana)       LIGACOES=$((NOS * THREADS)) ;;
    hierarquica) LIGACOES=$NOS ;;
    *) echo "Política desconhecida: $POLITICA (usa plana ou hierarquica)" >&2; exit 1 ;;
esac

# 1 e 2. Compilar e enviar o jar (saltado com SEM_BUILD=1, usado pelos scripts de varredura).
if [ -z "${SEM_BUILD:-}" ]; then
    mvn -q package -DskipTests
    ssh "$TORRE" "mkdir -p ~/batchpoint"
    scp -q target/batchpoint.jar docker/Dockerfile "$TORRE":~/batchpoint/
    ssh "$TORRE" "cd ~/batchpoint && docker build -q -t $IMAGEM . > /dev/null"
fi

# 3. Apagar contentores de uma execução anterior e, no fim desta (mesmo com erro ou Ctrl+C),
#    parar os desta. Ficam parados mas não apagados, para se poderem ler os logs.
NOMES="$(for i in $(seq 1 $NOS); do printf 'no%d ' "$i"; done)"
ssh "$TORRE" "docker rm -f $NOMES > /dev/null 2>&1 || true"
trap 'ssh "$TORRE" "docker stop -t 1 $NOMES > /dev/null 2>&1 || true"' EXIT

# 4. Arrancar os nós: cada um preso aos seus 2 cores (0-1, 2-3, 4-5), dentro da slice dos nós,
#    com a rede da torre (sem NAT). Ficam a tentar ligar até o coordenador abrir a porta.
for i in $(seq 1 $NOS); do
    C=$(( PRIMEIRO_CORE + (i - 1) * THREADS ))
    if (( THREADS > 1 )); then CORES="$C-$(( C + THREADS - 1 ))"; else CORES="$C"; fi
    ssh "$TORRE" "docker run -d --name no$i \
        --cgroup-parent=$SLICE --cpuset-cpus $CORES --network host \
        $IMAGEM --modo $POLITICA --id $i --threads $THREADS --carga $CARGA --lote $LOTE \
        --coordenador $COORD_IP:$PORTA > /dev/null"
done

# 5. Correr o coordenador no portátil.
java -cp target/batchpoint.jar pt.ulisboa.batchpoint.coordenador.Coordenador \
    --politica "$POLITICA" --ligacoes "$LIGACOES" --nome "$NOME" --sensor "$TORRE" "$@"
