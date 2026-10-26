# Setup da torre (PC fixo)

Projeto **batchpoint** (Programação Paralela e Concorrente). Gonçalo Lourenço (fc68092).
Configurado a 14 de outubro de 2026; Docker e switch acrescentados a 25 e 26 de outubro.

## Índice

1. [Visão geral](#1-visão-geral)
2. [Hardware](#2-hardware)
3. [Instalação: BIOS e Ubuntu](#3-instalação-bios-e-ubuntu)
4. [Acesso por SSH a partir do portátil](#4-acesso-por-ssh-a-partir-do-portátil)
5. [Configuração do sistema da torre](#5-configuração-do-sistema-da-torre)
6. [Rede portátil–torre](#6-rede-portátiltorre)
7. [Docker](#7-docker)
8. [Divisão dos cores](#8-divisão-dos-cores)
9. [Validação](#9-validação)
10. [Medições de referência](#10-medições-de-referência)
11. [Uso no dia a dia](#11-uso-no-dia-a-dia)

## 1. Visão geral

A torre faz de "cluster": corre 3 nós, cada um num contentor Docker preso a 2 cores. O portátil é o coordenador e liga-se à torre por um switch Gigabit dedicado.

```
Portátil (coordenador, 10.42.0.1)
   │  CAT6
Switch TP-Link TL-SG105S
   │  CAT6
Torre (10.42.0.2) ── Ryzen 7 3700X, SMT e boost desligados, 3,6 GHz fixos
   ├── no1  contentor, cores 0-1 ┐ cache L3 A
   ├── no2  contentor, cores 2-3 ┘
   ├── no3  contentor, cores 4-5   ┐ cache L3 B
   └── sistema e Docker, cores 6-7 ┘
```

| Valor de referência | Medido |
| --- | --- |
| Custo de uma iteração da carga sintética (core 0, Docker) | 1,6755 ns |
| Ida e volta do protocolo, portátil ↔ contentor (mediana) | ~120 µs |
| Largura de banda | 941 Mbit/s |

## 2. Hardware

### 2.1 Torre

| Componente | Detalhes |
| --- | --- |
| Modelo | ASUS ROG Strix GA15DH (pré-montado), BIOS versão 303 |
| Processador | AMD Ryzen 7 3700X, 8 cores físicos (16 threads com SMT) |
| Memória | 32 GB DDR4 (2 × 16 GB Corsair), D.O.C.P Profile#1 a 3200 MHz |
| Disco do Windows | INTEL SSDPEKNW512G8 (512 GB, NVMe, BitLocker). **Não mexer.** |
| Disco do Ubuntu | SSD externo Crucial X10 (Micron CT1000X10SSD9, 1 TB), ligado por USB-C |
| Placa de rede | `enp8s0` |

### 2.2 Portátil (coordenador)

| Componente | Detalhes |
| --- | --- |
| Modelo | Lenovo IdeaPad 5 2-in-1 14IRH9 |
| Processador | Intel Core i7-13620H, 16 GB de RAM |
| Sistema | Ubuntu 24.04.5 LTS |
| Utilizador | `slx` |
| Adaptador de rede | USB-C para Ethernet Gigabit, interface `enx00e06c79be37` |

### 2.3 Rede

| Componente | Detalhes |
| --- | --- |
| Switch | TP-Link TL-SG105S, 5 portas Gigabit, não gerido, dedicado (só o portátil e a torre) |
| Cabos | 2 × CAT6, 1000 Mbit/s: portátil ↔ switch e switch ↔ torre |

O switch não tem configuração: os IPs, a ligação partilhada e o `/etc/hosts` da secção 6.1 ficam iguais, com o switch no meio do cabo.

## 3. Instalação: BIOS e Ubuntu

### 3.1 BIOS

Entra-se com **Del** no arranque. O menu de arranque é **F8**.

| Definição | Valor | Notas |
| --- | --- | --- |
| EZ System Tuning | Normal | Não usar "Performance", que faz overclock |
| D.O.C.P | Profile#1, DDR4-3200 | Não mudar daqui em diante |
| Ordem de arranque | 1.º SSD externo (ubuntu), 2.º pen, 3.º Windows | Sem o SSD ligado, arranca o Windows |
| SMT, boost e C-states | Como vieram | São controlados no Ubuntu |
| QFan Control (CPU FAN) | Standard ou Turbo | Recomendado, para evitar thermal throttling |

### 3.2 Ubuntu Server

| Ecrã | Escolha |
| --- | --- |
| Imagem | `ubuntu-24.04.5-live-server-amd64.iso`, gravada numa pen com o "Criador de discos de arranque" |
| Tipo | Ubuntu Server (não o minimized), sem drivers de terceiros |
| Rede | DHCP automático (router); proxy em branco; mirror por omissão |
| Disco | *Use an entire disk* → SSD externo de 931.513G; LVM ligado, **sem encriptação** (LUKS desligado) |
| Perfil | Nome `Lourenco`; servidor `fixo`; utilizador `fixo` |
| SSH | *Install OpenSSH server*, com autenticação por palavra-passe |
| Snaps | Nenhum |

Resultado: Ubuntu 24.04.5 LTS, kernel `6.8.0-147-generic`.

## 4. Acesso por SSH a partir do portátil

No portátil:

```bash
ssh-keygen -t ed25519
ssh-copy-id fixo@192.168.1.90
```

Em `~/.ssh/config` do portátil:

```
Host fixo
    HostName 192.168.1.90
    User fixo
```

Para entrar: `ssh fixo` (sem palavra-passe).

> Durante a instalação a torre estava no router, com o IP `192.168.1.90`. Desde a mudança para o cabo direto (secção 6.1), o `HostName` é `10.42.0.2`.

## 5. Configuração do sistema da torre

### 5.1 Atualizar

```bash
sudo apt update && sudo apt upgrade -y
sudo reboot
```

### 5.2 Software

```bash
sudo apt install -y openjdk-21-jre-headless linux-tools-common linux-tools-$(uname -r) lm-sensors
sudo apt install -y iperf3 htop sysstat     # medir a rede e ver o uso dos cores
```

O Java solto só é preciso para testes sem Docker. Nas experiências, o Java vem dentro da imagem (secção 7.3).

No `iperf3`, responder **No** a "Iniciar o Iperf3 automaticamente como um daemon": o servidor só se liga quando é preciso (`iperf3 -s -1`).

### 5.3 Desligar o que interfere nas medições

```bash
# Impedir a suspensão
sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target

# Desligar as atualizações automáticas (não podem correr a meio de uma medição)
sudo systemctl disable --now unattended-upgrades apt-daily.timer apt-daily-upgrade.timer

# Serviços do sistema só nos cores 6 e 7 (os cores 0 a 5 ficam para os nós)
sudo systemctl set-property system.slice AllowedCPUs=6-7

# Tarefas agendadas que podem correr a meio de uma medição (ver todas com: systemctl list-timers --all)
sudo systemctl disable --now sysstat-collect.timer sysstat-summary.timer motd-news.timer \
    fwupd-refresh.timer update-notifier-download.timer update-notifier-motd.timer
```

As tarefas agendadas que ficam ligadas (`logrotate`, `man-db`, `fstrim`, `systemd-tmpfiles-clean`, `dpkg-db-backup`, `e2scrub_all`) são manutenção normal, correm nos cores 6-7 e a maioria é de madrugada ou semanal.

A 26 de outubro, o `systemctl list-timers --all` mostrou que o `sysstat-collect` (a cada 10 minutos), o `sysstat-summary` e o `motd-news` ainda estavam ativos; foram desligados nesse dia, com os outros três da lista acima.

Desligar também o aviso de upgrade para a 26.04 (**não fazer o upgrade**):

```bash
sudo sed -i 's/^Prompt=.*/Prompt=never/' /etc/update-manager/release-upgrades
grep Prompt /etc/update-manager/release-upgrades     # Prompt=never
```

### 5.4 SMT desligado pelo kernel

Escrever `off` em `/sys/devices/system/cpu/smt/control` dava **I/O error**, por isso o SMT é desligado com a opção de arranque `nosmt`. Fica só no GRUB do SSD do Ubuntu e não afeta o Windows.

```bash
sudo sed -i 's/^GRUB_CMDLINE_LINUX_DEFAULT="\(.*\)"/GRUB_CMDLINE_LINUX_DEFAULT="\1 nosmt"/' /etc/default/grub
sudo update-grub
```

Em `/etc/default/grub` fica: `GRUB_CMDLINE_LINUX_DEFAULT=" nosmt"`.

### 5.5 Serviço que prepara o CPU em cada arranque

Ficheiro `/etc/systemd/system/cpu-performance.service`:

```ini
[Unit]
Description=Configuracao do CPU para medicoes

[Service]
Type=oneshot
ExecStart=/usr/bin/cpupower frequency-set -g performance
ExecStart=/usr/bin/cpupower idle-set -D 2
ExecStart=-/bin/sh -c 'echo 0 > /sys/devices/system/cpu/cpufreq/boost'

[Install]
WantedBy=multi-user.target
```

| Linha | Efeito |
| --- | --- |
| `frequency-set -g performance` | Governador `performance`: frequência fixa |
| `idle-set -D 2` | Desativa os estados de repouso profundos (C-states), que demoram mais a acordar e penalizariam as tarefas pequenas |
| `echo 0 > .../boost` | Desliga o boost, para a frequência não variar com a temperatura |

Ativado com:

```bash
sudo systemctl enable cpu-performance.service
sudo systemctl daemon-reload
sudo reboot
```

## 6. Rede portátil–torre

### 6.1 Configuração (cabo direto a 14 de outubro; switch depois)

Foi feito todo por SSH, sem monitor na torre: prepara-se a rede nova com a torre ainda no router, aplica-se, e só depois se troca o cabo.

1. Na torre, impedir que a configuração de rede seja reescrita:
   ```bash
   echo 'network: {config: disabled}' | sudo tee /etc/cloud/cloud.cfg.d/99-disable-network-config.cfg
   ```
2. Na torre, substituir o conteúdo de `/etc/netplan/50-cloud-init.yaml` por:
   ```yaml
   network:
     version: 2
     ethernets:
       enp8s0:
         dhcp4: false
         addresses: [10.42.0.2/24]
         routes:
           - to: default
             via: 10.42.0.1
         nameservers:
           addresses: [10.42.0.1]
   ```
   validar sem aplicar:
   ```bash
   sudo chmod 600 /etc/netplan/50-cloud-init.yaml
   sudo netplan generate
   ```
3. No portátil, criar a ligação partilhada (o nome do adaptador vê-se com `nmcli device`):
   ```bash
   sudo nmcli connection add type ethernet ifname enx00e06c79be37 con-name cluster ipv4.method shared ipv4.addresses 10.42.0.1/24
   ```
4. Na torre, aplicar com `sudo netplan apply` (a sessão SSH congela; fechar com Enter, `~`, `.`).
5. Passar o cabo da torre do router para o adaptador do portátil e, no portátil:
   ```bash
   sudo nmcli connection up cluster
   sed -i 's/HostName 192.168.1.90/HostName 10.42.0.2/' ~/.ssh/config
   echo '10.42.0.2 fixo' | sudo tee -a /etc/hosts
   ```
   A linha no `/etc/hosts` é precisa porque, sem o router, o nome `fixo` deixa de ser conhecido pelo `ping` e pelo `iperf3` (o `ssh` usa o `~/.ssh/config`).
6. Na primeira ligação, o SSH pergunta se confia no novo endereço: a chave é a mesma da torre, responder `yes`.

> Até trocar o cabo, não reiniciar a torre: arrancaria já com `10.42.0.2`, que o router não conhece.

Mais tarde, o cabo direto foi substituído pelo switch TL-SG105S (secção 2.3), sem mudar nenhuma configuração: o adaptador do portátil e a torre ligam-se cada um a uma porta do switch.

### 6.2 Medições com o cabo direto (14 de outubro)

As da tabela abaixo foram feitas a 14 de outubro, **com o cabo direto**. Feitas a partir do portátil:

```bash
sudo ping -c 200 -i 0.002 fixo | tee data/testes/$(date +%Y-%m-%d_%H%M%S)_ping_cabo.txt
ssh fixo 'iperf3 -s -1' &
sleep 1
iperf3 -c fixo | tee data/testes/$(date +%Y-%m-%d_%H%M%S)_iperf_cabo.txt
```

| Medida | Wi-Fi (antes) | Cabo direto |
| --- | --- | --- |
| Latência, pings seguidos | ~2,8 ms, saltos até 12,8 ms | **0,117 ms** (mín. 0,100, máx. 0,187, desvio 0,012), 0% perdidos |
| Latência, ping normal (1 por segundo) | ~5,5 ms | 0,341 ms |
| Largura de banda (iperf3) | ~103 Mbit/s, instável | **941 Mbit/s**, estável |

A diferença entre os pings seguidos e o ping normal vem da poupança de energia entre pacotes. O valor que conta para as experiências, onde o tráfego é contínuo, é o dos pings seguidos.

### 6.3 Medições com o switch (26 de outubro)

São as que valem para as experiências. Feitas a partir do portátil, à corrente e em `performance`; os ficheiros estão em `data/rede/`.

```bash
sudo ping -c 200 -i 0.002 fixo | tee data/rede/ping_switch.txt
ssh fixo 'iperf3 -s -1'                          # noutro terminal
iperf3 -c fixo | tee data/rede/iperf_switch.txt
# PingPong: servidor no portátil, cliente num contentor da torre (core 0, rede host)
java -cp target/batchpoint.jar pt.ulisboa.batchpoint.ferramentas.PingPong --modo servidor
ssh fixo 'docker run --rm --cgroup-parent=batchpoint.slice --cpuset-cpus 0 --network host --entrypoint java batchpoint-no -cp /app/batchpoint.jar pt.ulisboa.batchpoint.ferramentas.PingPong --modo client --servidor 10.42.0.1:9000' | tee data/rede/pingpong_switch.txt
```

| Medida | Cabo direto | Com o switch |
| --- | --- | --- |
| Ping seguido (200 pacotes), média | 0,117 ms (desvio 0,012) | **0,134 ms** (mín. 0,085, máx. 0,252, desvio 0,035), 0% perdidos |
| Largura de banda (iperf3, 10 s) | 941 Mbit/s | **941 Mbit/s**, 0 retransmissões |
| Ida e volta do protocolo (`PingPong`, 10 000 trocas) | mediana 112,1 µs | **mediana 119,6 µs** (mín. 87,1, p99 286,0) |

O switch acrescenta ~7–17 µs por ida e volta e um pouco mais de variação, sem mudar a largura de banda. A ida e volta de **~120 µs** do `PingPong` é o valor usado para prever o ponto de cruzamento (H1).

## 7. Docker

Os nós correm em contentores Docker, um por nó. O coordenador continua solto no portátil.

### 7.1 Instalação (Docker CE, repositório oficial)

Na torre, um comando de cada vez:

```bash
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker fixo      # sair do SSH e voltar a entrar para o grupo contar
docker run --rm hello-world       # tem de mostrar "Hello from Docker!"
```

A torre descarrega os pacotes através da ligação partilhada do portátil. `docker info` mostra `Cgroup Driver: systemd` e `Cgroup Version: 2`.

### 7.2 A armadilha dos cores e a slice dos nós

O Docker põe os contentores dentro do `system.slice`, que está limitado aos cores 6-7 (secção 5.3). Um contentor só pode usar cores que o pai também tenha: com `--cpuset-cpus 0-1`, o kernel não dá erro e dá-lhe os cores do pai, **6-7**. Os três nós acabariam todos nos cores do sistema.

Teste que o mostrou:

```bash
docker run --rm --cpuset-cpus 0-1 --entrypoint cat batchpoint-no /sys/fs/cgroup/cpuset.cpus.effective
# 6-7   (errado)
```

Solução: uma slice própria para os nós, com os cores 0-5, e os contentores criados dentro dela (`--cgroup-parent`). Ficheiro `/etc/systemd/system/batchpoint.slice`:

```ini
[Unit]
Description=Nos do batchpoint (cores 0-5)

[Slice]
AllowedCPUs=0-5
```

Criado com:

```bash
printf '[Unit]\nDescription=Nos do batchpoint (cores 0-5)\n\n[Slice]\nAllowedCPUs=0-5\n' | sudo tee /etc/systemd/system/batchpoint.slice
sudo systemctl daemon-reload
```

A validação está na secção 9.2.

### 7.3 Imagem e execução

- A imagem `batchpoint-no` é construída na torre, em `~/batchpoint/`, a partir do `docker/Dockerfile` do projeto e do jar (base `eclipse-temurin:21-jre`, flags fixas da JVM).
- O `scripts/correr.sh`, no portátil, faz tudo: compila, copia o jar e o Dockerfile, constrói a imagem, arranca os contentores `no1` a `no3` com `--cgroup-parent=batchpoint.slice --cpuset-cpus … --network host`, corre o coordenador e, no fim, para os contentores.
- `--network host`: os contentores usam a rede da torre diretamente, sem NAT.
- Se um nó falhar: `ssh fixo docker logs no1` (ou `no2`, `no3`).

## 8. Divisão dos cores

| Função | Cores | Como |
| --- | --- | --- |
| Nó 1 | 0 e 1 | contentor `no1`: `--cgroup-parent=batchpoint.slice --cpuset-cpus 0-1` |
| Nó 2 | 2 e 3 | contentor `no2`: `--cgroup-parent=batchpoint.slice --cpuset-cpus 2-3` |
| Nó 3 | 4 e 5 | contentor `no3`: `--cgroup-parent=batchpoint.slice --cpuset-cpus 4-5` |
| Sistema operativo e Docker | 6 e 7 | `system.slice` |

Sem Docker (testes soltos), o equivalente é `taskset -c 0-1`, `2-3` e `4-5`.

Os cores 0-3 e 4-7 estão em dois grupos (CCX), cada um com a sua cache L3. Os nós 1 e 2 partilham uma cache; o nó 3 partilha a outra com o sistema. Como as tarefas quase não usam memória, o efeito deve ser pequeno, mas entra nas ameaças à validade.

Flags da JVM nos nós (fixas na imagem): `-Xms512m -Xmx512m -XX:+UseSerialGC`.

## 9. Validação

### 9.1 Sistema da torre

Na torre, antes de cada sessão de medições:

```bash
cat /sys/devices/system/cpu/smt/control /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor /sys/devices/system/cpu/cpufreq/boost; nproc; systemctl is-failed cpu-performance.service
```

Resultado obtido a 14 de outubro de 2026:

| Saída | Significado |
| --- | --- |
| `off` | SMT desligado |
| `performance` | Governador |
| `0` | Boost desligado |
| `8` | Cores físicos disponíveis |
| `inactive` | O serviço correu até ao fim, sem erros |

- **Java na torre:** OpenJDK 21.0.12.1 (2026-08-18), build `21.0.12.1+1-1-24.04.4-Ubuntu`. O portátil tem a mesma versão.
- **Temperatura:** `sensors | grep Tctl`, cerca de 43 °C em repouso.

### 9.2 Cores dos contentores

Na torre (fica permanente, sobrevive a reinícios; repetir depois de atualizar o Docker):

```bash
for c in 0-1 2-3 4-5; do docker run --rm --cgroup-parent=batchpoint.slice --cpuset-cpus $c --entrypoint cat batchpoint-no /sys/fs/cgroup/cpuset.cpus.effective; done
# 0-1, 2-3, 4-5   (certo)
docker run --rm --cgroup-parent=batchpoint.slice --cpuset-cpus 0-1 --entrypoint nproc batchpoint-no
# 2
```

## 10. Medições de referência

### 10.1 Calibração da carga sintética

A calibração em Docker (26 de outubro, 1,6755 ns por iteração, core 0) está em `data/calibracao/calibracao_torre.txt` e é a que se usa nas experiências:

| Alvo | Iterações |
| --- | --- |
| 10 µs | 5 968 |
| 100 µs | 59 682 |
| 1 ms | 596 821 |
| 10 ms | 5 968 208 |
| 100 ms | 59 682 075 |

Comando, no portátil:

```bash
ssh fixo 'docker run --rm --cgroup-parent=batchpoint.slice --cpuset-cpus 0 --entrypoint java batchpoint-no -cp /app/batchpoint.jar pt.ulisboa.batchpoint.ferramentas.Calibracao' | tee data/calibracao/calibracao_torre.txt
```

### 10.2 Custo do Docker

| Medida | Sem Docker | Com Docker |
| --- | --- | --- |
| Calibração (ns por iteração, core 0) | 1,6748 | **1,6752 / 1,6755** (duas medições) |
| Plana, 20 000 tarefas de 10 µs, cabo (portátil à corrente) | ~0,79 s (25 out) | **~0,79 s** (26 out) |

O Docker, com `--network host` e a slice certa, não acrescenta custo mensurável, nem no cálculo nem na comunicação.

### 10.3 Rede

Ver as secções 6.2 (cabo direto) e 6.3 (switch). Valor para as experiências: ida e volta de **~120 µs**. Em localhost, para comparação: mediana de 12,0 µs.

## 11. Uso no dia a dia

### 11.1 Ligar, medir e desligar

1. Ligar o SSD externo e o cabo de rede (ao adaptador do portátil) **antes** de ligar a torre.
2. Carregar no botão da torre e esperar cerca de 1 minuto. No portátil, a ligação `cluster` ativa-se sozinha quando o adaptador está ligado (se não, `sudo nmcli connection up cluster`).
3. No portátil, `ssh fixo`. O `fixo login:` que aparece no monitor da torre pode ser ignorado.
4. Antes de medir, correr a validação da secção 9.
5. **Preparar o portátil** (é ele que atende todas as mensagens; ver abaixo).
6. Correr as experiências no portátil, na pasta do projeto: `scripts/correr.sh <nome> <plana|hierarquica> <argumentos>`.
7. Para desligar: `ssh -t fixo sudo poweroff`, ou um toque curto no botão.

### 11.2 O portátil também conta

O coordenador atende uma mensagem a cada ~0,1 ms. Em bateria, o portátil poupa energia e responde mais devagar: a 26 de outubro, a mesma experiência deu ~1,0 s em bateria e ~0,79 s à corrente (~30% de diferença). Antes de medir:

```bash
cat /sys/class/power_supply/*/online      # tem de dar 1 (à corrente)
powerprofilesctl set performance
powerprofilesctl get                      # performance
```

E fechar o VS Code (a extensão Java gasta CPU em fundo) e o browser.

### 11.3 Cuidados

- Depois de uma atualização que traga um kernel novo, reinstalar `linux-tools-$(uname -r)`. Sem isso, o `cpupower` falha e o serviço deixa de fixar o governador.
- Não correr `do-release-upgrade`: manter a 24.04, igual ao portátil.
- Usar sempre o IP do cabo do portátil (`10.42.0.1`) como endereço do coordenador, nunca o do Wi-Fi (`192.168.1.84`).
- A torre não precisa de internet. Quando precisar (instalar pacotes, imagens Docker), tem-na através da partilha do portátil.
- Correr os comandos `ssh fixo …` sempre **no portátil** (prompt `slx@`). Corridos dentro da torre, a torre liga-se a si própria, pede a palavra-passe e os ficheiros ficam na torre.
- Depois de atualizar o Docker, repetir a validação dos cores (secção 9.2).
