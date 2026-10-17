# STUDIO-010 — G0: auditoria e plano técnico

Data: 17/10/2026.

**Baseline auditada (árvore efetiva, não presumida):**
- `git fetch` em ambos os repositórios: os PRs Extension #8 e Studio #12 (STUDIO-009) estão **merged**.

| Repositório | `main` | Branch desta missão |
|---|---|---|
| Nekkora-Studio | `1f7dbac` (merge do PR #12) | `feat/nekkora-studio-010` |
| Nekkora-Extension-Chrome | `15112b6` (merge do PR #8) | `feat/nekkora-studio-010-safe-capture` |

Regressão de referência (STUDIO-009, mesmo conteúdo da `main`):

| Repositório | Resultado |
|---|---|
| Studio | Windows 560/560; Linux 560 (559 + 1 pulado); golden 4/4 |
| Extensão | Java 284; Node 161; Chrome 262 |

## 1. O que existe hoje

### 1.1 Modelo (Studio)

| Peça | O que oferece ao PDD | Cuidado |
|---|---|---|
| `Automation` → `LogicalSession` (nome, descrição, notas) → `AutomationStep` | ordem de sessões e etapas, ações, alvo efetivo (capturado ou substituído na 009) | nome, descrição e notas de sessão/etapa são **texto livre do desenvolvedor**: podem conter dado de teste. Nunca exportados sem política (§4.4) |
| `AutomationStep.actionType()` | tipo da ação (FILL, CLICK, SELECT_OPTION, CHECK, PRESS_KEY, NAVIGATE, WAIT, OBSERVE…) | — |
| `ActionParameters` | origem do valor (literal, variável, credencial) | o PDD **nunca** usa valor, nome de variável nem apelido de credencial |
| `ElementReference` | tipo semântico, `accessibleName` (já filtrado pela `nekkora-privacy/2` na captura e reaplicado pela 4ª barreira `CapturePrivacy`), `sensitive`, `targetId` | localizadores e chaves de correlação nunca vão ao PDD |
| `StepContext(application, screenTitle, location)` | modelo de URL e título sanitizados, para separar telas | URL só como modelo; nada disso aparece no PDD |
| `ObservedEvidence` (STUDIO-005) | evidências observadas (navegação, mudança de documento…) | sinal para **não** agrupar através de uma troca de tela |
| Histórico (desfazer/refazer), `.nekkora` 1.0/1.1/1.2 | — | o PDD é **somente leitura** da automação: não entra no histórico e não muda o formato |

**Não existe captura visual em lugar nenhum:**
- nenhuma imagem no domínio, no Recorder, na persistência ou no Bridge;
- nenhuma gravação histórica pode "recuperar" uma tela.

A captura é feita **no momento autorizado**, com a página no estado do bloco.

### 1.2 Extensão

- **Permissões atuais:** `nativeMessaging`, `sidePanel`, `storage`, `scripting`, `webNavigation`, `alarms`. Host `127.0.0.1` e `localhost`; opcionais `http(s)://*/*` concedidas por site.
- **Nenhuma API de pixels** (`captureVisibleTab`, `debugger`, `tabCapture`): a busca no código não encontra nenhuma.
- **Autoridade de identidade:** o resolvedor somente leitura da STUDIO-009 (`debug.resolve`) reencontra o alvo de cada etapa por `targetId` ou descritor e dá a caixa do elemento na página. É o que as anotações vão usar, nunca um localizador.
- **Classificação de origem:** `LAB` = laboratório local. A STUDIO-009 já usa isso para restringir a sonda.

### 1.3 Política estrutural (`nekkora-privacy/2`)

Lida em `docs/chrome-security-001/` da extensão: modelo de ameaças, captura, armazenamento.

Princípio: só sai o que pertence a uma lista **fechada**. A elegibilidade é decidida **pela estrutura**, antes do vocabulário:
- só **portadores de nome de interface** (`button`, `label`, `legend`, `summary`, `a[href]`, papéis button/link/tab/menuitem/checkbox/radio/switch/treeitem) podem ter o texto aproveitado;
- e só se **todo** termo pertencer ao vocabulário controlado (1062 termos).

Conteúdo de negócio, tabelas, mensagens e texto livre nunca saem, mesmo que feitos de palavras do vocabulário. O Studio tem o espelho Java: `CapturePrivacy.interfaceText`, `isCarrier`.

### 1.4 Canal

- Bridge (`nekkora-bridge/1`): quadro máximo de **256 KiB** (`BridgeFrames.MAX_FRAME_BYTES`, Studio e host).
- Native Messaging: extensão → host até 64 MiB.

Uma imagem sanitizada, majoritariamente opaca e lisa, cabe com folga. Haverá limite próprio e recusa acima dele (§3.6).

### 1.5 Limites herdados da STUDIO-009 (não reabertos aqui)

| Limite | Efeito no PDD |
|---|---|
| Identidade gravada no documento principal não é reencontrada em iframe/shadow | anotação omitida |
| Sósias indistinguíveis nunca comprovados | anotação omitida para elas |
| A escala da JVM no 150% não é a escala do Windows | geometria das anotações vem só da extensão, em px CSS normalizados, independente da escala do Studio |
| Canal perdido = sessão encerrada no Studio | a captura em curso é descartada |

## 2. Experimentos no Chrome real (G0, Chrome for Testing 155.0.8059.39)

Feitos com uma cópia **temporária** da extensão, apagada no fim. Os roteiros devolvem **só metadados** (ok/erro, dimensões); nenhum pixel foi gravado.

| # | Experimento | Resultado |
|---|---|---|
| E1 | `chrome.tabs.captureVisibleTab` no service worker, só com a permissão de host `127.0.0.1` | **recusado**: "Either the '<all_urls>' or 'activeTab' permission is required." |
| E2 | Cópia com `activeTab` + `commands` (atalho); tecla pressionada **no sistema operacional** (gesto real) | **funciona**: 1280×720 = só a área de conteúdo da aba (viewport 1280×720, DPR 1), sem barras do navegador |
| E3 | Depois do gesto, chamada direta (sem novo gesto) na mesma aba | **também funciona**: a concessão do `activeTab` dura até a aba navegar ou fechar. Por isso o `activeTab` sozinho **não** é barreira suficiente (§3.2) |
| E4 | `OffscreenCanvas` no service worker | disponível: a sanitização pode acontecer **dentro** da fronteira de captura |

## 3. Captura visual segura — proposta

### 3.1 Alternativas avaliadas

| Caminho | Decisão |
|---|---|
| `<all_urls>` | recusado: amplia o acesso a todos os sites, contra a missão |
| `debugger` (CDP `Page.captureScreenshot`) | recusado: permissão poderosa (controle total da aba), barra de aviso, além do necessário |
| Captura de tela pelo Studio (Robot/sistema) | recusado: pega o que estiver por cima (notificações, outras janelas). O incidente da STUDIO-009 provou o risco |
| Reconstruir a página a partir do DOM (estilo html2canvas) | recusado: não é imagem real do Chrome |
| **`activeTab` + `commands`** | **adotado**: concessão temporária, só da aba ativa, só depois de um **gesto real no Chrome**; sem aviso de instalação. O próprio gesto passa a ser a autorização da captura |

**Permissões novas da extensão:**
- `activeTab`, justificada por E1/E2;
- `commands`: um atalho, sugerido `Alt+Shift+P`, "Capturar a tela para o PDD (Nekkora)".

Nenhuma permissão de host nova.

### 3.2 Fluxo (fail-closed em cada passo)

1. **Studio, bloco do rascunho:** "Capturar tela" envia `studio.pdd.capture.arm`. O comando leva o bloco (referência opaca), as etapas do bloco (`targetId` + descritor recuperável), a aba e a validade (≤ 60 s).
2. **Extensão:** aceita **só em aba LAB**; fora dela, `VISUAL_CAPTURE_NOT_ALLOWED`. Arma **uma** captura para aquela aba e aquela época da página. Sem destaque na página: a extensão só sinaliza no ícone, e a janela do Studio diz "pressione Alt+Shift+P no Chrome".
3. **Gesto:** o usuário (ou o agente de teste via teclado do sistema) pressiona o atalho no Chrome. O service worker exige ao mesmo tempo:
   - armação válida e não vencida;
   - aba ativa = aba armada;
   - origem LAB;
   - mesma época;
   - uso único.

   Gesto sem armação: nada acontece.
4. **Content script (documento principal):**
   - devolve o **plano visual**: viewport, DPR, rolagem, ficha do layout;
   - devolve as **regiões permitidas**: só portadores com texto que passa por `interfaceText` (§3.3) e molduras de campo;
   - devolve as **caixas das etapas**, pelo resolvedor somente leitura da 009 (por identidade, não por localizador).
5. **Service worker:**
   1. remove destaques do debug (o PDD não desenha nada na página);
   2. chama `captureVisibleTab` e pede de novo a ficha do layout;
   3. **fichas diferentes** (rolou, mudou o DOM, mudou o tamanho) → descarta, `PAGE_CHANGED_DURING_CAPTURE`;
   4. **dimensões da imagem ≠ viewport × DPR** → descarta, `GEOMETRY_MISMATCH`.
6. **Sanitização na própria fronteira** (`OffscreenCanvas`, service worker):
   - a tela nova começa **inteiramente opaca**;
   - copia da imagem bruta **apenas** os retângulos dos portadores permitidos;
   - desenha molduras **sintéticas** dos campos (nenhum pixel do campo é copiado);
   - codifica em PNG.
7. **Autoverificação:**
   - todo pixel fora dos retângulos de portadores tem de ser a cor opaca ou a cor da moldura sintética;
   - divergência → descarta, `SANITIZATION_CHECK_FAILED`;
   - imagem acima do limite → descarta, `SANITIZED_IMAGE_TOO_LARGE`.
8. A imagem bruta e o bitmap são liberados (`close()`), e as referências são anuladas. A imagem bruta **nunca** é gravada nem enviada:
   - nem `storage`, nem log, nem Bridge, nem disco;
   - nem área de transferência, nem relatório.

   Exceção ou cancelamento passam pelo mesmo `finally`.
9. **Evento `studio.pdd.captured`** com:
   - PNG sanitizado;
   - dimensões, DPR, instante, época;
   - política `nekkora-visual/1` e sha-256;
   - regiões **só por tipo e retângulo** (sem texto);
   - anotações por etapa, com retângulo normalizado e situação (`PLACED` ou o motivo da omissão).

   Falha → evento com o **código** da falha e nenhuma imagem.
10. **Host (3ª barreira):**
    - reabre o PNG e confere dimensões, tamanho e política;
    - **refaz a verificação de pixels** com o mesmo mapa de regiões;
    - divergência → recusa.
11. **Studio (4ª barreira):** a mesma verificação em Java antes de aceitar a imagem no rascunho.

Limite declarado (A8 do modelo de ameaças): a imagem bruta existe por instantes na memória do service worker, como a própria página existe na memória do Chrome. O objetivo é que ela não seja **retida, exportada nem enviada**.

### 3.3 Política visual `nekkora-visual/1` (lista de permissão)

| Região | Pixels reais? | Condição |
|---|---|---|
| Portador de nome (`isCarrier`) | **sim**, só o retângulo do elemento | todas as condições abaixo:<br>• documento principal;<br>• visível e dentro do viewport;<br>• texto **inteiro** passa por `interfaceText`;<br>• `::before`/`::after` sem `content` textual;<br>• sem `img`/`svg`/`canvas`/`video`/`iframe`/`object` dentro;<br>• sem imagem de fundo;<br>• sem `transform`;<br>• é o elemento do topo em pontos de amostra (`elementsFromPoint`: nada sobreposto, como tooltip, toast ou modal) |
| Campo editável (`input` não botão, `textarea`, `select`, `contenteditable`) | **não**: moldura sintética | documento principal, visível. Valor, autofill e placeholder nunca aparecem |
| Todo o resto | **não**: opaco | títulos, parágrafos, tabelas, mensagens, imagens, SVG, canvas, vídeo, iframes (mesma ou outra origem), shadow (aberto ou fechado), pseudo-elementos, sobreposições |

Consequências conscientes:
- A tela "Resultado" aparece com a área de resultado **opaca**: é conteúdo de negócio.
- O PDD não afirma o que não pode mostrar.
- Título de página que não é portador (`h1`) fica opaco.
- A política é revisável numa versão `/2`, nunca por heurística silenciosa.

### 3.4 Anotações

- Retângulo do alvo de cada etapa vindo da extensão:
  - pelo resolvedor somente leitura, na mesma época e no mesmo plano visual da imagem;
  - normalizado em `[0,1]` sobre o viewport, sem depender da escala do Studio.
- Alvo não comprovado (ambíguo, não encontrado, em quadro/shadow, fora do viewport) → **sem marcador**, com pendência "marcador indisponível".
- Os marcadores são **desenhados pelo Studio** sobre a imagem (prévia e PDF), nunca na página nem dentro do PNG sanitizado.
- Ficam fora do retângulo do controle, para não esconder o campo ou o botão.

### 3.5 Por que isto atende o "não negociável"

- O que sai da fronteira é construído por **lista de permissão**: começa opaco e só copia o que a política prova ser rótulo de interface do vocabulário.
- Uma falha da heurística de região produz **menos** imagem, nunca mais.
- A cópia é conferida pixel a pixel em três lugares: service worker, host e Studio.
- Nenhum caminho aceita "imagem segura" por ausência de detecção.

### 3.6 Fluxo de falhas (todas viram código + bloco "imagem indisponível")

`VISUAL_CAPTURE_NOT_ALLOWED`:
- origem não LAB.

`NOT_ARMED`, `ARM_EXPIRED`, `TAB_MISMATCH`:
- gesto sem armação;
- armação vencida;
- aba errada.

`PAGE_CHANGED_DURING_CAPTURE`, `GEOMETRY_MISMATCH`:
- página mudou entre o plano visual e a captura;
- dimensões incoerentes com viewport e DPR.

`SANITIZATION_CHECK_FAILED`, `SANITIZED_IMAGE_TOO_LARGE`:
- autoverificação de pixels falhou;
- imagem acima do limite.

`TAB_CLOSED`, `LINK_LOST`, `PERMISSION_REVOKED`, `CAPTURE_BUSY`:
- aba fechada;
- canal perdido;
- permissão revogada;
- duas capturas ao mesmo tempo.

## 4. Documento: decisões propostas para G1–G3 e G6–G8

### 4.1 Projeção
- Pacote novo `nekkora-studio-pdd`, só Java, dependente do domínio.
- `DocumentationProjection.of(automação)` é pura e **somente leitura**: nunca chama operação do domínio que altere estado, e os testes provam igualdade antes e depois.
- Contrato `nekkora-studio/pdd-visual/1.0.0` (§3.1 da missão), com `documentRevision` e `processRevision`:
  - `processRevision`: sha-256 do conteúdo do processo relevante ao documento — ordem de sessões e etapas, ações, tipo e nome seguro do alvo, contexto de tela;
  - excluídos: nomes livres, valores, revisões do domínio e decisões.

### 4.2 Agrupamento (G2)
- Conservador, só **dentro** da sessão. Abre bloco novo:
  - na mudança de contexto de tela (`location` ou `screenTitle`);
  - em evidência de navegação ou troca de documento entre etapas;
  - em etapa sem alvo que mude a tela (navegar).
- Dividir e juntar são operações do **rascunho**, nunca da automação.
- Juntar só é permitido entre blocos adjacentes da mesma sessão e do mesmo contexto.
- Nenhuma etapa se perde: a soma das etapas dos blocos = etapas da sessão, na ordem.

### 4.3 Instruções (G3)
Modelos determinísticos em português:

| Ação / tipo de alvo | Instrução |
|---|---|
| FILL em campo de senha (`sensitive`) | "Informar a senha" |
| FILL em outro campo | "Informar o campo “{nome}”" |
| CLICK em botão | "Selecionar “{nome}”" |
| CLICK em link | "Abrir “{nome}”" |
| SELECT_OPTION | "Escolher uma opção em “{nome}”" (nunca a opção) |
| CHECK / UNCHECK | "Marcar “{nome}”" / "Desmarcar “{nome}”" |
| PRESS_KEY | "Pressionar a tecla Enter" (só teclas do vocabulário fechado) |
| NAVIGATE | "Abrir a tela da aplicação" (nunca a URL) |
| sem modelo | "Executar a etapa {n}" + **Revisar descrição** |

- `{nome}` só quando passa por `CapturePrivacy.interfaceText`; senão "o campo"/"o botão" + **Revisar descrição**.
- Distinção explícita entre:
  - ação observada (etapa gravada);
  - intenção declarada (etapa manual);
  - resultado (nunca afirmado: "O resultado exibido não é verificado por este documento").

### 4.4 Textos livres
- Títulos de seção e de bloco propostos:
  - nome da sessão **só se** passar por `interfaceText`;
  - senão "Sessão {n}" + **Revisar**.
- Texto escrito ou revisado no editor fica marcado **revisado** (ator `DEVELOPER` ou `TEST_AGENT`). Ainda assim passa por um bloqueio de padrões:
  - sequências de 3+ dígitos, CPF/CNPJ, e-mail, URL;
  - identificadores técnicos (`DisplaySafety`).

  Se casar, a exportação fica bloqueada até corrigir.
- Pendência "Revisar" **impede exportar** (falha segura).

### 4.5 Persistência (decisão G0/G1)
- **`.nekkora` não muda** (1.0/1.1/1.2 intocados).
- Rascunho e imagens sanitizadas vão para armazenamento auxiliar versionado, no diretório de dados do Studio:
  - `pdd/<automationId>/rascunho.json` (`nekkora-studio/pdd-draft/1.0.0`);
  - `pdd/<automationId>/capturas/<sha256>.png`, só sanitizadas.
- Retenção: só imagens referenciadas pelo rascunho. Trocar ou descartar apaga a anterior; "Descartar rascunho" apaga a pasta.
- Gravação atômica (temporário + mover).

### 4.6 PDF (G7/G8)
- **Apache PDFBox 3.0.5** (Apache-2.0; baixa do Maven Central; dependência nova, isolada no módulo do PDD).
- Fonte **Inter**, já empacotada no design system, embutida como subconjunto. O texto fica pesquisável e com acentos.
- Página A4, texto à esquerda e imagem à direita, paginação sem cortes, marcadores vetoriais numerados.
- Metadados controlados: título fixo "PDD visual", produtor "Nekkora Studio", sem XMP, sem autor, sem caminho.
- Arquivo `pdd-visual-v001.pdf`, `v002`… no diretório escolhido:
  - nunca sobrescreve;
  - escreve um temporário no mesmo diretório, reabre e verifica (páginas, extração de texto sem sentinelas), depois move de forma atômica sem substituir;
  - falha limpa o temporário.
- Manifesto `pdd-visual-v001.manifest.json` com:
  - `processRevision`, `documentRevision`, hashes das imagens, versão, data, estado e limitações;
  - nenhum nome ou texto.

  O Studio indica "PDF desatualizado" quando a revisão do processo muda.

## 5. Laboratório

O laboratório da 009 só tem "Consulta de registro".

**Ampliação** (`test-lab/pages/studio010/`), só fictícia:
- `login.html`: Usuário, Senha, Entrar → `consulta`;
- `consulta.html`: Código, Consultar;
- `resultado.html`: área de resultado;
- **páginas adversariais com sentinelas por classe**:
  - campo preenchido e senha;
  - autofill;
  - tabela com nome/CPF fictícios;
  - toast, tooltip, modal;
  - imagem com texto, SVG/canvas com texto;
  - `::before` com texto;
  - iframe mesma/outra origem;
  - shadow aberto/fechado;
  - sobreposição, rolagem e zoom;
  - mudança de página entre o plano e a captura.

O oráculo continua no servidor.

## 6. Riscos

| Risco | Tratamento |
|---|---|
| Rótulo do vocabulário que, num contexto raro, é dado | mesmo risco residual da `privacy/2` (vocabulário sem sobrenomes comuns); documentado |
| Texto de outro elemento desenhado por cima do portador | amostragem `elementsFromPoint` + recusa de `transform`; risco residual de sobreposição sub-pixel documentado |
| Concessão do `activeTab` persiste após o gesto | máquina de estados própria (armação única, aba, época, validade); gesto sem armação não captura |
| Escala/DPI | imagem e anotações em px do dispositivo e normalizadas; homologação 1366×768 e 1920×1080 |
| Tamanho no Bridge | limite e recusa explícita; imagem lisa (opaca) é pequena |
| PDF expõe texto | só texto do rascunho aprovado; extração de texto e varredura de sentinelas antes de mover o arquivo final |

## 7. Plano por gate

| Gate | Entrega |
|---|---|
| G1 | módulo `nekkora-studio-pdd`: contrato, projeção pura, revisões, testes de determinismo e não mutação |
| G2 | agrupamento e dividir/juntar |
| G3 | instruções e políticas de texto |
| G4 | **extensão**: `activeTab` + `commands`, `studio.pdd.capture.*`, plano visual, sanitização e autoverificação; host com 3ª barreira; Studio com 4ª. Testes Node e Java e IT no Chrome real com sentinelas por classe |
| G5 | anotações (100/150%, rolagem) |
| G6 | editor e prévia Swing (4 temas, desfazer/refazer do rascunho) |
| G7 | PDF |
| G8 | versões e manifesto |
| G9 | homologação real (Robot + atalho real, 1366×768 e 1920×1080), regressões, relatório e PRs (extensão primeiro) |

## 8. Passagem do G0

A captura visual segura é **viável** e foi demonstrada nos pontos críticos no Chrome real (E1–E4). Ela depende de duas permissões novas e justificadas na extensão: `activeTab` e `commands`. Não há incompatibilidade material com a `nekkora-privacy/2` (ela não muda; a política visual é separada). Prossigo para o G1.

**Pontos para a usuária (não bloqueiam):**
- as duas permissões novas da extensão;
- a dependência nova PDFBox (Apache-2.0);
- a política visual por lista de permissão, que deixa opaco tudo que não for rótulo de interface (inclusive títulos de página e a área de resultado).
