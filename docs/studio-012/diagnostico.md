# NEKKORA-STUDIO-012 — Diagnóstico (G0)

Caso de reprodução: um projeto `.nekkora` local da usuária. Ele foi copiado para uma pasta privada, com hash e backup, e
só a CÓPIA foi lida. Aqui aparecem apenas metadados: tipo de ação, estratégia, escopo, códigos e contagens. Nenhum
nome, descrição, URL, identificador, valor ou texto da página.

## 1. O que o projeto continha

| Etapa | Ação | Modo | Localizador preferido | Escopo | Observações |
|---|---|---|---|---|---|
| 1 | Preencher (usuário) | com interação | XPath relativo | documento principal | `id`/`name` ausentes; sósias |
| 2 | Preencher (senha) | com interação | XPath relativo | documento principal | `id`/`name` ausentes; 2 campos de senha na página |
| 3 | Clicar (Entrar) | com interação | ID | documento principal | — |
| 4 | Clicar (link do menu) | com interação | posição | iframe 1 | nome retido; sósias |
| 5–8 | Selecionar opção | **sem interação** | posição | iframe 2 | a MESMA lista 4 vezes; opção não escolhida |
| 9 | Clicar (botão) | **sem interação** | NAME | iframe 2 | — |

Linha do tempo das etapas (horários de criação):
- login: 3 etapas no mesmo minuto;
- um único clique no iframe do menu;
- ~40 s sem nenhuma etapa;
- depois, só capturas sem interação.

As duas variáveis tinham Origem = Entrada e sensibilidade Segredo. A Finalidade de ambas era uma palavra única, com
forma de VALOR, e por isso é tratada como confidencial e não foi lida.

## 2. Causas

### Login: campos de usuário e senha

A causa não é iframe nem elemento filho. Os campos estão no documento principal, e a captura acertou o próprio
`input`, que fica dentro de 1 e de 3 envoltórios.

A página de login de homologação foi observada de forma passiva, sem digitar, clicar nem enviar nada:
- os dois campos TÊM `id` e `name`, únicos na página e sem sinais de geração dinâmica;
- os dois são formados por 2 termos;
- em cada um, um termo técnico de notação húngara está fora do vocabulário controlado: o prefixo `ds` no usuário e o
  sufixo `temp` na senha.

Pela `nekkora-privacy/2`, o identificador inteiro é retido quando qualquer termo fica fora do vocabulário. Sem `id` e
sem `name`, o Recorder caía no XPath relativo. O botão, que tem todos os termos no vocabulário, saiu por ID.

O Studio também não explicava o motivo: não havia diferença visível entre "o campo não tem id" e "o id foi retido pela
política".

### Menu Auditoria → Solicitação em estudo, e o nível

**Causa principal:** o Recorder não acompanhava iframes da mesma origem que trocam de documento.

O sensor de interações do documento principal liga ouvintes no `contentWindow` dos iframes da mesma origem.
- Quando o iframe NAVEGA (o menu que recarrega para abrir a seção, ou a tela carregada no frame de conteúdo), os
  ouvintes ficam presos ao documento antigo.
- O mesmo acontece com iframes inseridos depois do início da gravação.
- O rastreador de mutações recapturava o documento novo, mas o sensor de interações e o observador de consequências não
  se religavam.

Isso explica toda a linha do tempo:
- o primeiro clique no menu foi gravado;
- o clique em "Solicitação em estudo", já no documento recarregado, foi perdido;
- no modo sem interação a gravação "funcionava", porque trocar de modo religa os ouvintes;
- nesse modo, a lista de seleção não abre, então a opção não pôde ser escolhida, e a mesma lista acabou capturada
  quatro vezes.

**Reprodução.** O cenário sintético `test-lab/pages/studio012` (extensão) reproduz a estrutura:
- login com campos descendentes;
- portal com iframes de menu e de conteúdo;
- menu que recarrega;
- 15 listas iguais.

`Studio012RecorderIT` gravou login + botão + "Auditoria" e, depois que o frame recarregou, nenhuma etapa a mais
(`antes-da-correcao-2`). Com a correção, as 7 etapas chegam na ordem.

**Nível de auditoria.** A opção escolhida é VALOR do usuário pela `nekkora-privacy/2`: ela é identificada, mas não sai
da página. A etapa precisa ter a opção configurada no Studio.

A lista real não tem `id`, `name` nem rótulo `<label>` (o texto ao lado é célula de tabela) e convive com 14 listas
iguais. Por isso, o único localizador é estrutural e o debug guiado não consegue comprovar a identidade dela.

### Variáveis

O formulário abria na aba "Declaração", com "Finalidade" como campo de texto livre, enquanto "Origem" e "Valor fixo"
ficavam na segunda aba, escondidos quando a origem era Entrada. A Finalidade foi usada como lugar do valor.

O domínio da STUDIO-008 já separava os conceitos: `purpose` como descrição, `VariableOrigin` (CONSTANT, INPUT,
STEP_OUTPUT, UNBOUND) e `Sensitivity`. Faltava a interface deixar isso claro.

## 3. Achado durante a missão (gerador Python)

O pré-voo do gerador não olhava a trajetória de frames. Um alvo dentro de iframe geraria código que o `nekkora_rpa`
0.2.0 procura no documento principal: uma queda silenciosa para o documento errado.
