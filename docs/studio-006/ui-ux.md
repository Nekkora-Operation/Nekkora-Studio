# STUDIO-006 — Interface

Tudo com o Design System existente (nenhuma biblioteca visual nova): `NekkoraMenu`, `NekkoraMenuItem`,
`NekkoraDialog`, `NekkoraStatusBar`, `NekkoraConfirmationDialog`, diálogo de arquivos do sistema (`JFileChooser` com o
tema FlatLaf).

## Menu Arquivo

| Item | Atalho | Observação |
|---|---|---|
| Novo projeto | Ctrl+N | mesma ação de "Nova automação" |
| Abrir projeto… | Ctrl+O | filtro "Projeto do Nekkora Studio (.nekkora)" |
| Salvar | Ctrl+S | sem arquivo: pede o destino |
| Salvar como… | Ctrl+Shift+S | mesma identidade interna |
| Projetos recentes ▸ | — | até 10; removidos aparecem indisponíveis com "Retirar da lista" |
| Fechar projeto | — | pergunta se houver alterações |
| Recuperação automática… | — | consentimento, intervalo, limpeza |
| Sair | — | Salvar todos / Descartar alterações / Cancelar |

Os atalhos usam `NekkoraKeys.shortcut` (Ctrl no Windows/Linux, ⌘ no macOS) e não conflitam com os existentes (teste
S006-075/076 percorre o registro de ações). Mnemônico do menu: Alt+A. Salvar e Salvar como também estão na barra de
ferramentas.

## Indicadores

- **Aba**: `Consulta de Clientes *` enquanto há alterações não salvas; sem `*` depois do salvamento **confirmado**.
- **Barra de status** (primeiro item): `Nenhum projeto aberto`, `Alterações não salvas — sem arquivo`,
  `Salvo — consulta.nekkora`, `Alterações não salvas — consulta.nekkora`, `Salvando…`, `Erro ao salvar — …`,
  `Arquivo alterado externamente — …`, `Recuperado — alterações não salvas — …`. A dica lembra que projetos são
  arquivos locais não criptografados (ou mostra o erro).
- **Recuperação**: `Recuperação ativa` / `Recuperação desativada` na barra; "Recuperação disponível" é o diálogo ao
  abrir o Studio.
- **Avisos**: "Projeto salvo" (sucesso) só depois da confirmação; "Erro ao salvar" diz que o arquivo anterior foi
  preservado e que as alterações continuam abertas.

## Barra de status em janela estreita (defeito encontrado e corrigido)

A homologação real (janela de 870 px) mostrou os textos da direita sobrepostos aos da esquerda — o mesmo ocorre em
1366×768 com escala de 150% (≈ 910 px lógicos). Causa: `NekkoraStatusBar` usava `BorderLayout` WEST/EAST, que sobrepõe
os lados quando a soma passa da largura; a STUDIO-006 acrescentou a situação do projeto e a recuperação. Correção:
layout em linha única; sem espaço, o lado esquerdo (estado) tem prioridade e os últimos itens de cada lado saem
**inteiros**. Com espaço, as posições são as mesmas de antes. Teste: `LayoutRegressionTest.barraDeStatusSemSobreposicao`
(falha no código anterior, passa no novo). O item "Navegador" passou para logo depois da situação do projeto.

## Textos corrigidos

"Rascunho em memória" (cabeçalho da automação) e "Estado: Rascunho em memória — não salvo" (propriedades) deixaram de
ser verdade com projetos salvos: agora mostram só o status do domínio ("Rascunho"); a situação do arquivo está na
barra de status e na aba.

## Acessibilidade e temas

- Menu e diálogos com nomes acessíveis; foco inicial definido (diálogo de recuperação: caixa "Ativar"); Esc fecha;
  Enter confirma; navegação por Tab.
- Quatro temas: menu e diálogos usam tokens de cor; teste S006-075/076 nos quatro temas.
- Escala de 150%: tamanhos via `Scale.px`; diálogos com conteúdo rolável limitado à tela.
- Golden screenshots: **não regenerados** (a composição de referência não inclui a barra de status nem os novos
  diálogos; `GoldenScreenshotTest` passa sem mudanças nas imagens).
