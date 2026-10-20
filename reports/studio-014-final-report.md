# STUDIO-014 — relatório final

**AI Development Engine e garantia de qualidade da automação.** Período: 2026-10-19 a 2026-10-20.
Branch: `feat/nekkora-studio-014`, no Studio e na extensão.
- **Operador da homologação:** Claude, como agente de teste (`TEST_AGENT`).
- **Prompt-Mestre:** `v003 (0.3, sha256:b13c12e4…cf4c)`.
- **Framework:** `nekkora_rpa 0.2.0` (catálogo `sha256:7d79edc9…0013`).

## Resumo honesto

| Pergunta | Resposta |
|---|---|
| Analisado? | Sim. A auditoria G0 está em [g0-auditoria.md](../docs/studio-014/g0-auditoria.md). |
| Documentado? | Sim. Há o PDD visual e a especificação funcional versionada, com o gate `PDD_READY` persistido. |
| Gerado? | Sim. O gerador oficial cobre dois casos: <ul><li>execução única: `codegen-config/1.1.0`, novo;</li><li>lote tabular: `1.0.0`, mesmos bytes do gerador antigo.</li></ul> |
| Testado? | Sim, com ferramentas reais. <ul><li>Compilação, importação, `ruff check`, `ruff format --check`, `mypy --strict` e 7 testes de contrato do projeto gerado.</li><li>Controle negativo: um projeto estragado de propósito é reprovado.</li></ul> |
| Executado? | Só pela homologação, em laboratório local autorizado, com oráculo independente: <ul><li>Caso A: o servidor registrou a consulta, e o resultado lido confere;</li><li>Caso B: a réplica conferiu 10 de 10 submissões em ordem e corretas.</li></ul> O Studio **não executa**: V6 fica sempre "não verificado". |
| Sites públicos (objetivo final) | **Sim.** Três sites de treinamento foram mapeados e desenvolvidos pelo Studio, e o código gerado foi executado uma vez em cada um: **21/21**, as três execuções `COMPLETED`. Quatro defeitos achados foram corrigidos no produto: Studio, extensão e framework. Ver [sites-publicos.md](../docs/studio-014/sites-publicos.md). |
| Anthropic | **Integração real não homologada.** A chamada real com a chave da usuária respondeu HTTP 400, porque a chave não está ligada a um workspace. O cabeçalho do workspace já é enviado quando configurado, e falta o id. Antes: Foram provados o contrato (servidor HTTP local que imita o provedor) e o fluxo na interface (provedor MOCK identificado como mock). Nenhuma chamada real foi feita, e o Claude Code não foi usado como substituto do provedor. |

## Gates

| Gate | Estado | Evidência |
|---|---|---|
| G0 auditoria | ✓ | matriz EXISTE / PARCIAL / NÃO EXISTE / BLOQUEADO; sem PRs abertos nem base divergente |
| G1 fluxo simples, explicação e catálogo | ✓ | "Desenvolver automação" sem formulário; "O que vou utilizar" com arquivo e linha do pacote instalado e seção da documentação (captura `S014-A-03`) |
| G2 `PDD_READY` | ✓ | `PddGate` + `GateRecord`. Invalidação provada: o modelo mudou → `PDD_OUTDATED`, e nenhum arquivo foi gravado (C1) |
| G3 plano, variáveis e geração | ✓ | variáveis derivadas numa operação desfazível; Pages por tela; `SingleGenerator`; regressão tabular com os mesmos bytes |
| G4 validação por camadas | ✓ | V0–V6, correções seguras, revisar e aplicar, pedidos de ajuda, bloqueio de segurança |
| G5 UI | ✓ | 4 temas, 1366×768 e 150%: botões visíveis; a aba Validação rola em vez de sobrepor |
| G6 testes | ✓ | Windows 692/692; Linux 686 + 4 pulados (antes dos sites públicos); mutação 17/17 |
| G7 homologação real | ✓ | 41/41 e, em 150%, 6/6 (logs em `docs/studio-014/evidence/`); sites públicos 21/21 (`evidence/sites/`) |
| G8 qualidade e compatibilidade | ✓ | validação sem rede; cancelamento; trabalho em segundo plano fora da EDT; PDD antigos lidos como antes (testes da STUDIO-010/012/013 verdes) |
| G9 PRs | abertos, sem merge | ver "PRs" abaixo |

## Testes por ambiente

| Ambiente | Resultado |
|---|---|
| Windows 11, MS OpenJDK 21.0.12 (`./mvnw test`) | **692/692**, 0 pulados (690 + ordem dos imports + site público autorizado). Os 3 testes "Python real" rodaram com a venv do framework: Python 3.12.10, ruff 0.17.1, mypy 2.4.0. |
| Linux, Temurin 21.0.12 em contêiner fixado (`tools/golden-linux.sh verify`, HEAD `8a05d21`) | **686 aprovados + 4 pulados**, 0 falhas. Pulados: <ul><li>1 só para Windows, o mesmo de sempre;</li><li>3 "Python real", porque não há o interpretador do framework no contêiner. Eles **não** foram dados por aprovados.</li></ul> |
| Testes novos da missão | 34: <ul><li>motor: 28 (prompt 3, catálogo 4, gate 7, fluxo de execução única 4, contrato da IA 5, endereço inicial 2, capacidades 3);</li><li>gerador: `SingleConfigTest` 1;</li><li>app: `DevelopmentStudioTest` 5.</li></ul> |
| Controle negativo (mutação) | **17/17 mortas**: gate, especificação, verificador da IA, consentimento, chave, variáveis, endereço, iframe, origem, ruff, contrato, gate no gerador, idempotência do envio, PDD bloqueado, V6, resposta fora do modelo. A M14 sobreviveria a uma checagem fraca; o teste foi reforçado e ela morre pela razão certa. |

## Homologação real (Studio + Chrome for Testing + Robot)

- **Caso A**, simples e sem planilha (laboratório `studio014`, dados fictícios):
  - gravação real: 7 etapas, nenhum valor no modelo;
  - a área do resultado vira Observar;
  - debug guiado: tudo `CONFIRMED`;
  - PDD revisado.
- **Desenvolver automação:**
  - um clique;
  - a única pergunta é o endereço inicial, mascarado pela privacidade, e uma resposta com `?` é recusada;
  - resultado `CODE_VALIDATED`, com as 6 verificações reais.
- **Validar automação:** `V1 PDD_READY · V4 5/5 · V5 7 testes · V6 não executada pelo Studio`.
- **Execução** do código gerado no laboratório:
  - o oráculo do servidor registrou **exatamente 1** consulta nova, com o código da entrada;
  - o processo leu "situação SUSPENSO", que é a resposta do oráculo;
  - os eventos não contêm usuário, senha, código nem resultado.
- **Caso C**, ajuda:
  - **C1** PDD desatualizado: bloqueia sem tocar no código; "Abrir PDD" → atualizar → revisar → a análise retoma e regenera.
  - **C2** efeito desconhecido ("Calcular"): perguntas objetivas por etapa (efeito, conclusão, endereço); respondidas, gera o envio uma vez só, confirmado pela etapa seguinte.
  - **C3** alvo em iframe: pedido de ajuda com o caminho de extensão do framework; nenhum código.
- **Caso D**, IA com MOCK rotulado:
  - a prévia do envio mostra o que vai e o que não vai;
  - "Não enviar" não chama o provedor;
  - "Enviar" chama uma vez;
  - o verificador recusa o StepId inventado;
  - a proposta aceita vira decisão com base `PROPOSAL_ACCEPTED`.
- **Caso B**, regressão tabular, com o projeto real da E2E-001 e a configuração declarada da réplica:
  - mesmos bytes do gerador antigo, pelo menu Avançado;
  - na réplica local, o oráculo dela conferiu 1 início e 10 submissões em ordem, todas corretas;
  - placar lido: 100%.
- **150%** (4 temas): a janela conclui em `CODE_VALIDATED`; botões visíveis; a aba Validação mostra as camadas sem sobreposição.

## Defeitos achados e corrigidos

| Achado | Como apareceu | Correção |
|---|---|---|
| Código gerado com linhas acima de 120 colunas (E501) | ruff real no teste "Python real" | cabeçalho e docstrings do emissor encurtados |
| Origem não reconhecida num endereço-modelo `{…}` (o parser de URI recusava) | testes do app com dados no formato da privacidade | origem lida por expressão tolerante ao modelo |
| Endereço inicial impossível de derivar da gravação (privacidade) | mesma investigação | etapa Navegar ou pergunta conferida contra o modelo (`START_URL`) |
| Sem diretório de dados, o desenvolvimento gravava em `%TEMP%` | revisão do código | `DevelopmentStore.inMemory()` |
| Camadas não chegavam à aba Validação (ouvinte não ligado) | `DevelopmentStudioTest` | ligação na composição do Studio |
| Troca de automação sobrescrevia o resultado novo | mesmo teste | só age quando a automação ativa muda |
| Linha de camadas cortada (V6 invisível) na coluna estreita | captura da homologação | forma compacta `V0 ! · … · V6 —`; o detalhe vai na dica e no leitor de tela |
| 150%: cabeçalho e rodapé da Validação sobrepostos | captura em 150% | o painel rola; o divisor respeita a altura mínima |
| Com o PDD bloqueado, o iframe não aparecia | homologação C3 | diagnóstico V2 junto das pendências do PDD |
| Envio idempotente passaria despercebido (teste fraco) | mutação M14 | teste reforçado |
| Imports de telas fora da ordem do ruff (I001) | site público, V5 real | `SingleEmitter.natural` (D-S014-20) |
| Nome de link perdido por seta decorativa `aria-hidden` | site público (quotes), gravação | captura na extensão e na segunda linha Java (D-S014-21) |
| Glifo de ícone e descendente `aria-hidden` no nome pelo Playwright | execução do código gerado nos sites | framework, PR próprio (D-S014-22) |
| Erro do provedor sem a mensagem dele | chamada real à Anthropic (HTTP 400) | `AIException.detail()` e cabeçalho `anthropic-workspace-id` opcional |

## PRs (abertos, SEM merge)

Ordem sugerida: o PR da extensão primeiro, porque só traz o laboratório e não muda nada no Studio. Depois o do Studio.
Os sites públicos geraram também um PR no framework (`fix/nome-com-glifo-de-icone`). A ordem é: framework,
depois extensão, depois Studio.

## O que permanece limitado

Ver [known-limitations.md](../docs/studio-014/known-limitations.md). Em resumo:
- V6 nunca é verificada pelo Studio;
- Anthropic real não homologada;
- execução gerada só para laboratório;
- sem iframe ou shadow DOM;
- a leitura do resultado depende de o elemento surgir com o resultado;
- o lote tabular continua com configuração declarada;
- V4/V5 exigem um Python com `nekkora_rpa`, ruff e mypy.
