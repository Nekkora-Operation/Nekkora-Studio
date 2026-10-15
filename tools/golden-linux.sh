#!/bin/sh
# Ambiente de referência dos golden screenshots Linux (homologação visual).
#
# Roda os testes num contêiner com JDK fixado por digest, sobre uma cópia do commit atual (git archive HEAD):
# alterações não commitadas não entram, e o repositório local não é montado para escrita.
# Se o repositório Nekkora-Extension-Chrome estiver ao lado deste, uma cópia do HEAD dele é colocada na mesma
# posição dentro do contêiner, para o teste de contrato do PageMap rodar.
#
# Uso (na raiz do repositório):
#   tools/golden-linux.sh golden   só GoldenScreenshotTest
#   tools/golden-linux.sh verify   ./mvnw clean verify completo (padrão; compila o Native Host da extensão ao lado,
#                                  se houver, para os testes cruzados do Bridge)
#   tools/golden-linux.sh update   regrava os 4 golden Linux e copia SÓ eles para golden/linux/
#
# Atualizar a imagem de referência é uma decisão de homologação: troque IMAGE e TAG juntos, rode "update",
# revise as imagens e registre a mudança (ver docs/CONTRIBUINDO.md).
set -eu

TAG="eclipse-temurin:21.0.12.1_1-jdk-resolute"   # Temurin 21.0.12.1+1, Ubuntu 26.04
IMAGE="eclipse-temurin@sha256:442a743d9272be15c9872915eab0f7a1b6bb45b7c16c613acf172e2d7483061d"
PLATFORM="linux/amd64"
MODE="${1:-verify}"

case "$MODE" in golden|verify|update) ;; *) echo "uso: $0 [golden|verify|update]" >&2; exit 2 ;; esac
[ -f pom.xml ] && [ -d nekkora-swing-ui ] || { echo "rode na raiz do Nekkora-Studio" >&2; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git archive --format=tar -o "$WORK/studio.tar" HEAD
if [ -d ../Nekkora-Extension-Chrome/.git ]; then
    git -C ../Nekkora-Extension-Chrome archive --format=tar -o "$WORK/extension.tar" HEAD
    echo "extensão: $(git -C ../Nekkora-Extension-Chrome rev-parse --short HEAD)"
fi
mkdir -p "$WORK/out"

cat > "$WORK/run.sh" <<'EOF'
#!/bin/sh
set -u
mkdir -p /nk/Nekkora-Studio && tar -xf /in/studio.tar -C /nk/Nekkora-Studio
if [ -f /in/extension.tar ]; then mkdir -p /nk/Nekkora-Extension-Chrome && tar -xf /in/extension.tar -C /nk/Nekkora-Extension-Chrome; fi
# verify: com a extensão ao lado, compila o Native Host dela (sem testes) para os testes cruzados do Bridge
if [ "$1" = verify ] && [ -f /nk/Nekkora-Extension-Chrome/java-core/native-host/pom.xml ]; then
  (cd /nk/Nekkora-Extension-Chrome && chmod +x mvnw && ./mvnw -B -ntp -q -pl java-core/native-host -am package -DskipTests) \
    && echo "native host da extensão compilado" || echo "native host da extensão NÃO compilado (testes cruzados serão ignorados)"
fi
cd /nk/Nekkora-Studio && chmod +x mvnw
java -version 2>&1 | sed -n 2p
case "$1" in
  golden) ./mvnw -B -ntp test -pl nekkora-swing-ui -am -Dtest=GoldenScreenshotTest -Dsurefire.failIfNoSpecifiedTests=false; rc=$? ;;
  verify) ./mvnw -B -ntp clean verify; rc=$? ;;
  update) ./mvnw -B -ntp test -pl nekkora-swing-ui -am -Dtest=GoldenScreenshotTest -Dsurefire.failIfNoSpecifiedTests=false \
            -Dnekkora.golden.update=true; rc=$?
          mkdir -p /out/linux && cp nekkora-swing-ui/src/test/resources/golden/linux/*.png /out/linux/ ;;
esac
exit $rc
EOF

# Git Bash no Windows: caminhos de volume no formato do Windows e sem conversão automática.
host_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
echo "imagem: $TAG ($PLATFORM, $IMAGE)"
set +e
MSYS_NO_PATHCONV=1 docker run --rm --platform "$PLATFORM" --label nekkora.golden=linux \
    -v "$(host_path "$WORK"):/in:ro" -v "$(host_path "$WORK/out"):/out" -v nekkora-studio-m2:/root/.m2 \
    "$IMAGE" sh /in/run.sh "$MODE"
rc=$?
set -e

if [ "$MODE" = update ]; then
    for theme in sakura-dark midnight-dark porcelain-light sage-light; do
        cp "$WORK/out/linux/$theme.png" nekkora-swing-ui/src/test/resources/golden/linux/
    done
    echo "golden Linux regravados; revise antes do commit:"
    git status --short nekkora-swing-ui/src/test/resources/golden
fi
exit $rc
