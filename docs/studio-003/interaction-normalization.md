# STUDIO-003 — Normalização das interações

A normalização dos **eventos** em operações funcionais acontece na extensão, porque precisa do DOM: alvo funcional,
label → controle, select/option, ativação de checkbox. As regras R1 a R11 e seus testes estão em
`Nekkora-Extension-Chrome/docs/studio-003/interaction-recorder.md`.

Ela é determinística: a mesma sequência de eventos produz exatamente o mesmo resultado (teste "determinismo"), sem
IA.

## O que chega ao Studio

Uma operação por etapa: `CLICK`, `FILL`, `SELECT_OPTION`, `CHECK`, `UNCHECK`, `PRESS_KEY` ou `UNKNOWN`. Ela vem com
a referência do elemento, a opção (select), a tecla (Enter), o contexto e as evidências.

Exemplos com eventos reais do navegador (Chrome real, `Studio003InteractionsIT`):

| Gesto do usuário | Eventos | Etapa |
|---|---|---|
| clicar em "Usuário", digitar 9 letras, ir para "Senha" | click, 9× input, focusout | `FILL` "Usuário" (eventos consolidados: 10) |
| digitar 300 caracteres em "Observações" | 300× input | **uma** etapa `FILL` |
| clicar em "Entrar" (envia o formulário e navega) | pointer/mouse, click, submit, navegação | `CLICK` "Entrar" com evidência `FORM_SUBMITTED` (sem segundo clique) |
| Enter em "Pesquisa" | keydown Enter, clique sintético no botão padrão, submit | `FILL` + `PRESS_KEY` Enter com `FORM_SUBMITTED` (nenhum clique inventado) |
| escolher "São Paulo" | input, change | **uma** etapa `SELECT_OPTION` com a opção como parâmetro |
| marcar pela label, desmarcar no controle | click na label, click no input, change | `CHECK`, `UNCHECK` |
| a página faz `.click()`/`dispatchEvent` | eventos não confiáveis ou change sem ativação | nenhuma etapa (UNTRUSTED, PROGRAMMATIC_CHANGE) |
| clicar no ícone dentro do botão | click no span | `CLICK` no **botão** |
| clicar num `span` com cursor de mão | click | `UNKNOWN` (alvo ambíguo, nenhum ancestral escolhido) |

## Nome da instrução no Studio (`RecordedStepDrafts`)

Os nomes são determinísticos, a partir da ação, da categoria semântica e do nome acessível:

| Ação | Nome |
|---|---|
| FILL | "Preencher campo {nome}" |
| CLICK (botão / link / item de menu / aba / lista) | "Clicar no botão {nome}" / "Clicar no link {nome}" / "Clicar no item de menu {nome}" / "Clicar na aba {nome}" / "Clicar na lista {nome}" |
| CLICK (outros) | "Clicar em {nome}" ou "Clicar no elemento <tag>" |
| SELECT_OPTION | "Selecionar {opção} no campo {nome}" ou, sem opção, "Selecionar opção no campo {nome}" |
| CHECK | radio: "Marcar opção {nome}"; outros: "Marcar {nome}" |
| UNCHECK | "Desmarcar {nome}" |
| PRESS_KEY | "Pressionar Enter no campo {nome}" |
| UNKNOWN | "Interação não classificada em {nome ou <tag>}" |

O nome só entra se `DisplaySafety.isSafeToDisplay`. Ele é limitado a `Texts.NAME_MAX` caracteres; acima disso, é
cortado com "…".
