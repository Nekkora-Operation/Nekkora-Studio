# STUDIO-008 — Interface

## Onde

- **Painel de propriedades da automação** (grupo Ações): botão "Variáveis e dependências" (descrição acessível com a
  quantidade declarada). A barra de ferramentas não ganhou botão: a 1366 px ela já não tem espaço sobrando.
- **Atalho**: Ctrl+Shift+B.
- **Painel inferior com abas** "Validação | Variáveis": o mesmo espaço do painel de validação da STUDIO-007; uma aba por
  vez; Esc ou × fecha o painel.
- **Validação → variável**: um apontamento de escopo Variável mostra "Variável — nome" e o botão "Ir para variável",
  que abre a aba Variáveis com ela selecionada.
- O menu Automação não mudou (D-S008-10).

## Painel "Variáveis e dependências"

- Aviso fixo: "Declarações do processo. O Studio não guarda valores de execução e não executa nada; a origem de saída de
  etapa é declarada, não comprovada."
- Totais: "N variáveis · X em uso · Y sem uso".
- Lista: "nome · Tipo · Significado" e, abaixo, "Origem · Escopo · usada em N etapas · Sensibilidade". O valor fixo
  nunca aparece na lista.
- Detalhe da selecionada: origem declarada (com "(não comprovada)" para saída de etapa), significado (confirmado ou
  proposto), escopo, sensibilidade, restrições, e "Usada por: Sessão 1 · Etapa 02 — …" (até 3 e "e mais N").
- Botões: Nova variável, Editar, Renomear (F2), Excluir (Delete), Ir para uso. Duplo clique/Enter edita.
- Durante uma gravação: consulta permitida; criar, editar, renomear e excluir desabilitados (mesmo bloqueio das outras
  edições, com o aviso de sempre).

## Formulário de variável

Três abas curtas (cabem em janelas baixas e a 150%):

1. **Declaração**: nome, tipo técnico, significado, "Confirmo o significado desta variável" (desmarcada por padrão),
   sensibilidade, finalidade.
2. **Origem e escopo**: escopo (automação ou sessão), origem; para Valor fixo, o campo e o aviso "Não criptografado";
   para Saída de etapa, a etapa de origem ("Declaração sua: o Studio não comprova que esta etapa produz o valor").
3. **Restrições**: obrigatória, comprimento, formato, limites, casas decimais, moeda (os campos que não se aplicam ao
   tipo ficam desabilitados).

O domínio decide; cada recusa aparece no campo certo e a aba dele vem para frente.

## Renomear e excluir

- Renomear mostra a prévia ("1 etapa passa a usar o novo nome, na mesma operação.") e a lista das etapas atualizadas;
  a notificação final diz quantas etapas mudaram e lembra o Desfazer.
- Excluir em uso pede confirmação destrutiva: "Excluir e desligar N etapas".

## Editor de etapa (aba Parâmetros)

Com a origem "Variável", aparece "Variáveis declaradas": a lista das visíveis na sessão da etapa. Escolher preenche o
nome; digitar continua possível. Abaixo do campo: "Declarada: Texto · CPF · origem entrada" ou "Não declarada: a
validação aponta…".

## Temas, escala e acessibilidade

- 4 temas (Sakura Dark, Midnight Dark, Porcelain Light, Sage Light) — `VariablesUiTest.temasEAcessibilidade` e
  capturas da homologação.
- 150% em 1366×768 lógicos: painel e botões inteiros, área de trabalho utilizável, abas do formulário dentro de um
  diálogo grande — `Variables150Test`.
- Nomes acessíveis: "Variáveis e dependências", "Variáveis declaradas", "Origem declarada", "Declaração", "Usada por",
  "Painel inferior: validação e variáveis", campos de comprimento e limites nomeados. Linhas lidas com o texto
  completo. Nenhuma informação só por cor.
