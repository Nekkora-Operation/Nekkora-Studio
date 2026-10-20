# STUDIO-014 — objetivo final: três sites públicos mapeados e desenvolvidos pelo Studio

Pedido da usuária: escolher 3 sites públicos, mapeá-los e desenvolver a automação de cada um **a partir do Studio**, com
o resultado em `Documentos/teste-rpa/<site>/`. Erros encontrados são corrigidos no produto (Studio, extensão,
framework), **nunca** no código gerado.

## Sites escolhidos

Os três são sites feitos para praticar automação e publicam as próprias credenciais de demonstração. Nenhum dado
pessoal foi usado.

| Pasta | Site | Fluxo gravado | Leitura (Observar) |
|---|---|---|---|
| `the-internet/` | `https://the-internet.herokuapp.com/login` | Username, Password, Login → ler → Logout | link "Logout" (só existe na área segura) |
| `quotes-toscrape/` | `https://quotes.toscrape.com/login` | Username, Password, Login → ler → Next → Logout | link "Logout" |
| `saucedemo/` | `https://www.saucedemo.com/` | Username, Password, Login → ler → Open Menu → Logout | botão "Open Menu" (só existe na área logada) |

Cada pasta tem:
- `<site>.nekkora`, o projeto salvo pelo Studio;
- `<site>-python/`, o código gerado pelo "Desenvolver automação";
- `execucao-resumo.txt`, o resumo da execução única.

O `(formato 1.0.0).nekkora` é a cópia que o próprio Studio guarda ao atualizar o formato no "Atualizar e salvar".

## Como foi feito, sempre pela interface do Studio

1. **Recorder real** no Chrome for Testing, com cliques reais do mouse:
   - preenchimentos em CAPTURE_AND_INTERACT, sem valor no modelo;
   - a leitura gravada em CAPTURE_ONLY e convertida em Observar.
2. **Debug guiado:** cada alvo foi confirmado no site, e as correções foram feitas por seleção no navegador.
3. **PDD visual revisado**, com o ator `TEST_AGENT`.
4. **Desenvolver automação:**
   - a pergunta de segurança `ORIGIN_NOT_AUTHORIZED_FOR_EXECUTION` foi respondida por "Autorizar como site público de treinamento…", com confirmação (D-S014-19);
   - o código foi gerado no ambiente `benchmark` e validado: compilação, importação, ruff check e format, mypy estrito e testes de contrato.
5. **Execução única** do código gerado (Chromium headless), fora do Studio, com as credenciais de demonstração só em
   variáveis `NK_SECRET_*`.

Resultado da rodada final (`evidence/sites/SITES-homologacao-log.txt`): **21/21 verificações**.

| Site | Código | Execução | Lido pelo processo |
|---|---|---|---|
| the-internet | `CODE_VALIDATED` | `COMPLETED`, 1 transação | `Logout` |
| quotes-toscrape | `CODE_VALIDATED`, depois de "Revisar e aplicar" | `COMPLETED`, 1 transação | `Logout` |
| saucedemo | `CODE_VALIDATED` | `COMPLETED`, 1 transação | `Open Menu` |

Nenhuma senha aparece no modelo, no código, nos eventos nem no log.

## Erros achados e onde foram corrigidos

| # | Sintoma | Causa | Correção (produto) |
|---|---|---|---|
| 1 | V5 reprovou o código: `ruff check` I001 | `from paginas import TelaAcesso, Tela2` fora da ordem do isort | Studio, `SingleEmitter.natural` (D-S014-20) |
| 2 | Execução do the-internet: `TARGET_IDENTITY_MISMATCH:name` no botão Login | o Playwright inclui o glifo de fonte de ícones (``) no nome | framework, `accessible_name` (S014-A1) |
| 3 | Link "Next" do quotes gravado SEM nome; o alvo virou posicional | a seta `<span aria-hidden>→</span>` reprovava o texto inteiro do portador na política de vocabulário | extensão (`capture.js`) e segunda linha Java (`SnapshotPrivacy`, `PageModel`) (D-S014-21) |
| 4 | Execução do quotes: `TARGET_IDENTITY_MISMATCH:name` no Next | com `include_hidden=True`, o Playwright calcula "Next →" | framework, nome exposto primeiro (S014-A2) |

Depois da correção 3, o Studio propôs ("Revisar e aplicar") trocar o localizador posicional do Next pelo `role name`. A
proposta foi aplicada e o alvo reconfirmado no debug guiado, como o próprio Studio pede.

## Comportamentos do produto observados (corretos, não defeitos)

- **quotes.toscrape:** o próprio site rotula a senha com `for="username"`. Pela especificação (e no Chrome), o campo de
  usuário se chama "Username Password" e o de senha fica sem nome. O Studio registra isso fielmente, e o código gerado
  identifica a senha pelo seletor, sem nome.
- **saucedemo, "Add to cart":** o botão de UM produto só se distingue pelo nome do produto (`add-to-cart-<produto>`). A
  privacidade retém esse dado de negócio, e os 6 botões "Add to cart" ficam idênticos. A gravação marca o alvo como não
  resolvido, e o Studio não gera localizador posicional. O cenário do saucedemo foi refeito sem escolher produto.
- **saucedemo, título "Products":** é texto não interativo. O Recorder descarta o clique (`NON_INTERACTIVE`), como na
  limitação 6.
- **saucedemo, Logout no menu lateral:** gravado logo depois da animação do menu, veio como `ELEMENT_UNRESOLVED`
  ("revisar"). O debug guiado acusou a divergência, e a correção por seleção confirmou o alvo pelo `data-test`.

## Evidências

`evidence/sites/`:
- o log da rodada final, sem senhas e com os caminhos locais higienizados;
- as capturas da janela "Desenvolver automação": pergunta de segurança, confirmação da autorização e a proposta de troca de localizador.

As capturas do resultado ficaram fora porque mostram o caminho local da conta.
