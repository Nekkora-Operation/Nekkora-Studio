# NEKKORA-STUDIO-012 — Relatório final

Missão corretiva: gravação de iframes, login, menu e nível; preferência por id/name; experiência de variáveis.

Branches `feat/nekkora-studio-012` no Studio e na Extensão, criadas das `main` pós-merge da E2E-001:
- Studio `8288672`;
- Extensão `8f247fe`;
- Framework `ac8133c`, que não mudou.

Integração: **Extensão → Studio**. Sem merge.

Documentos: `docs/studio-012/{diagnostico,decisions,homologation,security,known-limitations}.md` e
`docs/studio-012/evidence/`.

## 1. Causa raiz por cenário

| Cenário | Causa comprovada | Correção |
|---|---|---|
| Campos de usuário e senha | Não era iframe nem elemento filho: a captura já acertava o `input`. O `id`/`name` existe, é único e estável, mas tem um termo técnico ("ds", "temp") fora do vocabulário, e o identificador inteiro era retido. Daí o XPath. | Lista técnica `nekkora-identifier-terms/1` (aprovada), só para id/name/data-testid; nota `IDENTIFIER_WITHHELD`; motivo da estratégia exibido no Studio. |
| Auditoria → Solicitação em estudo | Os ouvintes do Recorder ficavam no documento ANTIGO quando o iframe do menu recarregava (e o de conteúdo navegava). O clique seguinte não era observado. O modo sem interação "funcionava" porque trocar de modo religa os ouvintes. | Sensor por documento, religado a cada documento de frame aceito pela captura. |
| Nível Auditoria Carefy | Mesma causa. Sem interação, a lista não abre, então a opção não é escolhida (4 capturas da mesma lista). A opção escolhida é valor do usuário: é retida pela política e precisa ser configurada no Studio. | Gravação com interação volta a funcionar; o debug confere a opção CONFIGURADA (contagem e sim/não) quando a identidade da lista é comprovada. |
| Variáveis | A Finalidade ficava na 1ª aba; Origem e Valor ficavam na 2ª, escondidos para Entrada. | Aba "Nome e valor" com a Origem do valor; Finalidade marcada como descrição; segredo sem valor; Finalidade com forma de valor recusada; "onde é usada" e como vincular. |
| Achado: gerador | Alvo em iframe geraria código que age no documento principal. | Pré-voo `TARGET_IN_FRAME_NOT_SUPPORTED`. |

## 2. Antes e depois (laboratório que reproduz a estrutura)

| | Antes | Depois |
|---|---|---|
| Fluxo com interação | 4 etapas; nada depois que o frame do menu recarrega | 7 etapas na ordem, dentro dos iframes corretos |
| Login | XPath (id retido) | ID |
| Hover | clique sem explicação | clique com `REVEALED_BY_HOVER` → obstáculo "dependência de outra interação" (revisão) |
| Nível | sem conferência possível | debug: "Opção «Auditoria Carefy»: existe na lista e está selecionada agora" (lista identificável); falha fechada na lista sem identificador |
| Variável | valor na Finalidade aceito | recusado no campo, com a origem do valor na 1ª aba |

## 3. Testes

| Suíte | Resultado |
|---|---|
| Extensão: Node | 181/181 |
| Extensão: Java (núcleo, host, contrato) | 295/295 |
| Extensão: Chrome for Testing, todas as classes em 10 grupos, headless | 266/266, incluindo `Studio012RecorderIT` 4/4 (`Studio010SafeCaptureIT` exige janela e se desliga em headless: rodada com janela à parte) |
| Studio: Windows `./mvnw test` | 608/608 |
| Studio: homologação real (Studio, Chrome, Robot) | 25/25 |

**Testes novos:**
- vetores da lista técnica (JS, Java e Studio);
- normalizador de hover;
- `Studio012RecorderIT`: fluxo, hover, sósias, nível;
- cliente do debug com a opção;
- Recorder do Studio (notas novas);
- `ElementPresentationTest`;
- formulário de variáveis (Finalidade, origem, constante, Desfazer/Refazer, onde é usada);
- debug guiado pela interface (4 cenários);
- gerador (iframe).

**Cenários negativos cobertos:**
- `name` repetido e `id` duplicado;
- `id` gerado e `id` fora das listas;
- evento delegado;
- opção inexistente ou não configurada;
- lista sem identidade;
- segredo com constante;
- Finalidade com forma de valor;
- alvo em iframe no gerador.

## 4. Trilha real da usuária

- **Feito:**
  - cópia protegida, com backup e hash; o original segue intacto;
  - auditoria estática da cópia, só por metadados;
  - observação passiva do login de homologação;
  - a cópia abre no Studio novo, sem escrita.
- **Pendente da usuária:** a gravação autenticada no ambiente de homologação, com ela digitando a credencial (roteiro em
  `docs/studio-012/homologation.md`). A credencial informada no chat não foi gravada nem usada por mim. Recomendação:
  trocar a senha.
- **Na cópia:** as duas variáveis têm Finalidade com forma de valor e devem ser corrigidas localmente.

## 5. Limitações principais

Ver `known-limitations.md`.
- A lista de nível real não tem identificador: continua em revisão, e o debug não confere a opção.
- O nome do item de menu fica retido (vocabulário não ampliado).
- Hover por script não é detectado.
- O runtime Python não tem frame, seleção nem hover: geração bloqueada para alvo em iframe.
