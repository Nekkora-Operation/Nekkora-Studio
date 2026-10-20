# NEKKORA-PACKAGING-001 — segurança

## Cadeia de suprimentos

- **Framework**: wheel embutido no Studio, construído por `Nekkora-AI-Engine/tools/empacotar_framework.py`
  (`git archive` do commit homologado, `core.autocrlf=false`, `build --no-isolation` com ferramentas travadas por
  hash, dois builds idênticos exigidos, em contêiner Linux). `artefatos.json` guarda versão, commit e SHA-256; o
  Studio confere o hash ao materializar o arquivo, antes do pip.
- **Dependências**: `--require-hashes --only-binary=:all:` contra o PyPI oficial; um índice que sirva outro arquivo
  com o mesmo nome falha com HASH_MISMATCH (testado com índice adversarial local).
- **Ferramentas de verificação** (ruff, mypy): lock próprio com hash.
- `--isolated`: configuração de pip do usuário e variáveis `PIP_*` não alteram a instalação. Proxy por
  `HTTPS_PROXY` continua respeitado (redes corporativas); falha de proxy/certificado vira PROXY_OR_SSL.
- Release do framework (quando autorizado): Trusted Publishing (OIDC), `id-token: write` só nos jobs de upload,
  environments com aprovação humana, artefatos construídos e verificados antes do upload e conferidos por SHA-256,
  actions fixadas por SHA de commit, guardas que falham fechado (licença, classifier, tag = versão, versão inédita,
  TestPyPI antes do PyPI). Nenhum token de longa duração.

## Consentimento e escopo

- Nada é instalado sem a confirmação explícita da pessoa no diálogo do plano; recusar não cria nada (teste
  `ambienteSemConsentimento` + homologação).
- A IA não instala, não escolhe versão nem URL. O consentimento da IA (Anthropic) e o da instalação são separados.
- Nada do projeto (`.nekkora`, PDD, planilhas, credenciais, PDFs) é enviado ao índice — o pip só recebe nomes e
  versões do lock.
- Nada vai para o Python global (homologação compara `pip freeze` antes/depois) nem para a pasta do projeto.

## Segredos e dados

- Nenhuma chave, token, planilha real ou caminho de cliente nos repositórios. A chave Anthropic não é usada nesta
  missão.
- Saída do pip sanitizada antes de aparecer na interface ou nos logs: credenciais em URL viram `***`, a pasta do
  usuário vira `~`.
- PRs abertos pela API com a credencial do git, sem imprimir token.
