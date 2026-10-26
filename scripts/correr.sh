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
NOS=3
THREADS=2
IMAGEM=batchpoint-no
SLICE=batchpoint.slice      # slice com os cores 0-5 (ver setup_torre.md)

# A carga e a porta têm de ser as mesmas no coordenador e nos nós: procura-as nos argumentos.
CARGA=sintetica
PORTA=9000
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]} - 1; i++)); do
    case "${ARGS[$i]}" in
        --carga) CARGA="${ARGS[$((i + 1))]}" ;;
        --porta) PORTA="${ARGS[$((i + 1))]}" ;;
    esac
done

case "$POLITICA" in
    plana)       LIGACOES=$((NOS * THREADS)) ;;
    hierarquica) LIGACOES=$NOS ;;
    *) echo "Política desconhecida: $POLITICA (usa plana ou hierarquica)" >&2; exit 1 ;;
esac

# 1. Compilar o jar (os testes correm à parte, com mvn package).
mvn -q package -DskipTests

# 2. Copiar o jar e o Dockerfile para a torre e construir a imagem lá.
#    O Docker reaproveita as camadas que não mudaram, por isso só o jar é refeito.
ssh "$TORRE" "mkdir -p ~/batchpoint"
scp -q target/batchpoint.jar docker/Dockerfile "$TORRE":~/batchpoint/
ssh "$TORRE" "cd ~/batchpoint && docker build -q -t $IMAGEM . > /dev/null"

# 3. Apagar contentores de uma execução anterior e, no fim desta (mesmo com erro ou Ctrl+C),
#    parar os desta. Ficam parados mas não apagados, para se poderem ler os logs.
NOMES="$(for i in $(seq 1 $NOS); do printf 'no%d ' "$i"; done)"
ssh "$TORRE" "docker rm -f $NOMES > /dev/null 2>&1 || true"
trap 'ssh "$TORRE" "docker stop -t 1 $NOMES > /dev/null 2>&1 || true"' EXIT

# 4. Arrancar os nós: cada um preso aos seus 2 cores (0-1, 2-3, 4-5), dentro da slice dos nós,
#    com a rede da torre (sem NAT). Ficam a tentar ligar até o coordenador abrir a porta.
for i in $(seq 1 $NOS); do
    C=$(( (i - 1) * THREADS ))
    CORES="$C-$(( C + THREADS - 1 ))"
    ssh "$TORRE" "docker run -d --name no$i \
        --cgroup-parent=$SLICE --cpuset-cpus $CORES --network host \
        $IMAGEM --modo $POLITICA --id $i --threads $THREADS --carga $CARGA \
        --coordenador $COORD_IP:$PORTA > /dev/null"
done

# 5. Correr o coordenador no portátil.
java -cp target/batchpoint.jar pt.ulisboa.batchpoint.coordenador.Coordenador \
    --politica "$POLITICA" --ligacoes "$LIGACOES" --nome "$NOME" "$@"
