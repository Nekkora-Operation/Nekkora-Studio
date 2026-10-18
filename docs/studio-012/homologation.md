# NEKKORA-STUDIO-012 — Plano de testes e homologação

## Trilha sintética reprodutível

O laboratório `Nekkora-Extension-Chrome/test-lab/pages/studio012` usa só dados fictícios. Ele reproduz a estrutura do
caso de reprodução:

| Página | O que tem |
|---|---|
| `login.html` | Campos descendentes de envoltórios; `id`/`name` técnicos `ds…`/`…Temp`; formulário de troca de senha oculto (sósia); botão `Button_DoLogin`. |
| `portal.html` | `#mainContainer > div > iframe`: menu e conteúdo, mesma origem. |
| `menu.html` | "Auditoria" recarrega o próprio frame com a seção aberta; "Solicitação em estudo" carrega a tela no frame de conteúdo; "Cadastros" abre só com CSS `:hover`. |
| `solicitacao.html` | 15 listas sem `id`/`name`; a 7ª é o nível, com "Auditoria Carefy"; botão "Gravar" que registra o nível (oráculo). `?variante=nome` dá à lista o `name="ddlTipo"`. |
| `sosias.html` | `name` repetido, `id` duplicado, `id` gerado a cada carga, `id` fora das listas, campo só com rótulo, evento delegado no ancestral. |

### Chrome real (extensão, `Studio012RecorderIT`, Chrome for Testing headless)

| Cenário | Resultado |
|---|---|
| **Antes da correção:** fluxo completo com interação | Falha reproduzida: login + botão + Auditoria, e nada depois que o frame recarrega. |
| Fluxo completo com interação: login → Auditoria (frame recarrega) → Solicitação em estudo (tela no outro frame) → nível → Gravar | 7 etapas na ordem; login por ID; menu e tela dentro de iframe; opção retida; oráculo da página = "Auditoria Carefy"; debug AMBÍGUO e sem conferência de opção na lista real sem identificador. |
| Submenu só por `:hover` | 1 CLICK com `REVEALED_BY_HOVER`; nenhuma ação inventada; seção por recarga sem a nota. |
| Sósias e casos adversariais | `name` repetido e `id` duplicado nunca preferidos; `id` gerado e `id` fora das listas retidos com `IDENTIFIER_WITHHELD`; campo só com rótulo por LABEL/ROLE_NAME; clique delegado sem etapa (NON_INTERACTIVE contado). |
| Confirmação do nível (variante com `name`) | Lista por NAME; debug: "Auditoria Carefy" com 1 correspondência e selecionada; outra opção existe e não está selecionada; opção inexistente com 0 correspondências; nenhuma mensagem vinda da extensão contém o rótulo. |

### Studio real (`Studio012Homologation`, Windows + Robot)

O log saneado está em `evidence/homologacao-log.txt`. Resultado: 25 verificações, 0 falhas.

| Gate | Verificação |
|---|---|
| G1 | 7 etapas COM interação, na ordem; todas `INTERACTION_OBSERVED`; menu e tela dentro de iframe; opção retida e depois configurada no Studio; oráculo da página = "Auditoria Carefy". |
| G2 | Usuário e senha por ID (não XPath); senha sem `id` no descritor; lista com `name` técnico por NAME. |
| G3 | Finalidade "Senha2026x" recusada no campo (aba Descrição e escopo em primeiro plano, nada declarado); segredo com valor constante desabilitado e ajuda `NK_SECRET_<NOME>`; segredo declarado com Finalidade descritiva; vínculo explícito da etapa visível em "onde é usada". |
| G4 | Debug guiado mostra "Opção «Auditoria Carefy»: existe na lista e está selecionada agora" (Proposto). |
| Hover | Só o clique vira etapa; obstáculo de dependência de outra interação (`:hover`). |
| Persistência | Salvar e reabrir íntegros (StepIds, opção, variável); `.nekkora` sem valores digitados. |
| Projeto real | A cópia abre no Studio novo (9 etapas); nada a salvar ao fechar; hash inalterado; nenhuma captura de tela dela. |

## Trilha de reprodução da usuária

| Item | Situação |
|---|---|
| Auditoria estática da cópia (`evidence/auditoria-copia-metadados.txt`) | Feita: abre sem erro; 9 etapas; login por XPath; menu posicional em iframe; 4 seleções da mesma lista sem interação e sem opção; duas Finalidades com forma de valor. |
| Observação passiva do login de homologação | Feita: `id`/`name` presentes e únicos; retidos por "ds" e "temp" (causa do XPath). |
| Gravação autenticada no ambiente de homologação | **Pendente, feita pela usuária** (ver roteiro abaixo). A credencial não foi gravada em arquivo e não pode atravessar evidências. |

### Roteiro para a usuária (ambiente de homologação, conta de teste)

1. Instalar a extensão e o Studio desta branch, ou esperar os merges.
2. No Studio: nova automação, uma sessão, e Gravar na aba do login, no modo **com interação**.
3. No navegador:
   - digitar usuário e senha você mesma;
   - Entrar → Auditoria → Solicitação em estudo → abrir uma solicitação de TESTE → escolher "Auditoria Carefy";
   - **não clicar em Gravar** se isso alterar o registro.
4. Finalizar a gravação e salvar o projeto numa pasta local.
5. Me passar o caminho do arquivo. Eu rodo a mesma auditoria de metadados e confiro:
   - login por ID;
   - Auditoria e Solicitação em estudo gravados dentro do iframe;
   - seleção com interação.

## Regressões

| Suíte | Resultado |
|---|---|
| Extensão: Node (`node --test`) | 181/181 |
| Extensão: Java (núcleo, host, contrato) | 295/295 |
| Extensão: Chrome for Testing (todas as classes) | 278/278 (266 headless em grupos + `Studio010SafeCaptureIT` 12/12 com janela) |
| Studio: Windows (`./mvnw test`) | 608/608 |
| Studio: homologação real | 25/25 |
