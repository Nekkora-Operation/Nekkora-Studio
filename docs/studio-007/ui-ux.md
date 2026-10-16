# STUDIO-007 — Interface

## Acesso

- **Menu Automação** (novo, mnemônico U): Nova sessão, Nova etapa, ─, **Validar automação**.
- **Barra de ferramentas**: "Validar automação" (ícone de verificação), depois de Salvar.
- **Atalho**: Ctrl+Shift+V (sem conflito; o teste de atalhos repetidos continua passando).
- Habilitado só com uma automação ativa.

## Painel "Validação da automação"

Fica abaixo da área de trabalho, como um painel de problemas de IDE, oculto até a primeira validação. A divisória
começa em 55% para a área de trabalho e pode ser arrastada.

```text
Validação da automação                                    [↻] [×]
┌───────────────────────────────────────────────────────────────┐
│ ⊗ Estado: Configuração incompleta                              │
│   Consulta de Clientes: Foram encontrados bloqueios de         │
│   configuração. Esta análise não executou nenhuma etapa e não  │
│   consultou o navegador.                                       │
└───────────────────────────────────────────────────────────────┘
× 7 bloqueios   ! 2 avisos   i 0 informações
Mostrar: [Todos os apontamentos ▾]
┌───────────────────────────────────────────────────────────────┐
│ ⊗ ×  Bloqueio · Sessão 1 · Etapa 01 — Preencher usuário        │
│      Valor obrigatório não configurado.                        │
│ ⊗ ×  Bloqueio · Sessão 1 · Etapa 01 — Preencher usuário        │
│      Esta ação atua sobre um elemento, mas a etapa não tem…    │
└───────────────────────────────────────────────────────────────┘
Como corrigir: Edite a etapa e informe a origem do valor: …
[→ Ir para etapa] [✎ Editar etapa]
```

- **Estado**: título "Estado: <rótulo>", explicação do estado e a observação de que nada foi executado. Cor de erro
  (incompleta), aviso (avaliação parcial) ou informação (sem bloqueios estáticos — nunca "sucesso").
- **Resultado desatualizado**: quando a automação muda depois da validação, o título vira "Resultado desatualizado —
  último estado: …", aparece um aviso com a ação "Validar novamente" e a descrição acessível do painel muda. O relatório
  antigo nunca é apresentado como atual.
- **Totais**: linha única com símbolos; a dica e a descrição acessível trazem também etapas analisadas, etapas sem
  apontamentos e a versão das regras.
- **Filtro**: todos, bloqueios, avisos, informações.
- **Lista**: cada linha começa com símbolo e nome da severidade, depois o lugar (sessão, etapa, nome atual) e, na
  segunda linha, a explicação. Duplo clique ou Enter = Ir para etapa.
- **Como corrigir**: orientação do apontamento selecionado (linha única; texto completo na dica e no nome acessível).
- **Ir para etapa / sessão / automação**: seleciona o lugar no Studio.
- **Editar etapa**: abre o editor de etapa EXISTENTE (tipo, parâmetros, localizadores propostos); indisponível quando a
  automação está recebendo uma gravação ou a etapa não existe mais. Não há editor dentro do painel.
- **[↻]** valida de novo; **[×]** ou **Esc** fecha (o relatório fica guardado).
- Automação ativa sem validação: "Automação ainda não validada" — nunca o relatório de outra automação.

## Design system e acessibilidade

- Só componentes do design system (`NekkoraInlineAlert`, `NekkoraList`, `NekkoraComboBox`, `NekkoraButton`,
  `NekkoraIconButton`, `NekkoraText`), nenhuma biblioteca nova, nenhuma API visual direta (`DesignLintTest`).
- Quatro temas: testes de renderização e troca real de tema (Ctrl+Shift+T) na homologação.
- Severidade nunca só por cor: símbolo (× ! i) e nome em cada linha. "×" (U+00D7) em vez de "✕" (U+2715): a fonte do
  design system não tem o segundo, que aparecia como quadrado vazio (achado da conferência visual).
- Leitores de tela: `NekkoraList` passou a expor cada linha (nome acessível do renderizador = texto principal +
  secundário) — melhoria do design system que vale também para as listas de etapas; painel, lista, filtro, totais,
  "Como corrigir" e botões com nomes acessíveis.
- Teclado: Ctrl+Shift+V, setas/Enter na lista, Tab entre controles, Esc fecha; foco vai para a lista ao abrir.
- Janelas estreitas e baixas: linhas únicas com reticências, filtro em linha própria, altura mínima que nunca
  sobrepõe cabeçalho e rodapé; teste a 150% em 1366×768 lógicos (`Scale150Test.validacao150`) achou e corrigiu a área
  de trabalho com altura zero. A barra de status não muda.
