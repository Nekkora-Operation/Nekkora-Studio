# STUDIO-004 — Editor de etapas

O editor é o `StepDetailsForm` (a visualização de detalhes da STUDIO-001) evoluído. Ele abre com **F2**, **Enter** na
lista, duplo clique, **Editar etapa** no painel ou no menu de contexto. Tem cinco abas:

| Aba | Conteúdo |
|---|---|
| **Geral** | Nome, tipo de ação, sessão e descrição (o mesmo `StepForm` da criação), mais o resumo (tipo, sessão, posição, origem, estado, alterada manualmente) |
| **Parâmetros** | Origem do valor (nenhum, literal, variável ou credencial) e opção (rótulo e valor técnico), conforme o tipo de ação. Ver [parameters.md](parameters.md) |
| **Elemento** | Referência capturada, localizadores capturados (preferencial só entre os validados) e localizadores propostos manualmente. Ver [element-reference-editor.md](element-reference-editor.md) |
| **Comentários** | Adicionar, editar e excluir. Ver [comments.md](comments.md) |
| **Origem e alterações** | Origem, observação da gravação e valores capturados × atuais, com "Restaurar original". Ver [step-provenance.md](step-provenance.md) |

## Uma edição = uma operação

O que o usuário muda nas abas fica no formulário. **Salvar alterações** chama `WorkspaceService.editStep`, e
`Automation.editStep` então:

1. valida nome, descrição, tipo, sessão, parâmetros (compatibilidade com o tipo, literal em elemento sensível),
   localizador preferencial (só validado), localizadores manuais e comentários;
2. só então aplica tudo, conta uma revisão e cria uma entrada no histórico;
3. sem diferença, não conta revisão.

**Cancelar** (ou Esc) descarta tudo. Se o domínio recusar, a mensagem aparece no campo certo, a aba desse campo é
mostrada e o diálogo continua aberto.

## Tipo de ação × parâmetros

O parâmetro salvo é sempre o que o **tipo selecionado** aceita: um modo de valor antigo, escondido, não vence a opção
digitada. Se o que está configurado não cabe no tipo novo (ex.: `FILL` com variável → `SELECT_OPTION`), o Studio
**pergunta**, listando só o que será removido:

```
Remover parâmetros incompatíveis?
O tipo "Selecionar opção" não aceita: Variável cliente.estado. Para salvar, isso será removido da etapa.
[Cancelar] [Remover e salvar]
```

A opção digitada (ex.: "São Paulo"/"SP") é salva.

Se o usuário recusar, nada muda e o campo mostra o motivo. Nada é descartado em silêncio. O domínio também recusa
(`VALUE_NOT_ACCEPTED`/`OPTION_NOT_ACCEPTED`) qualquer edição que chegue com parâmetros incompatíveis.

## Sessão de destino

Escolher outra sessão no editor leva a etapa para o **final** dela, com a mesma identidade. A posição exata é escolhida
pelo diálogo **Mover etapa** (ver [step-ordering.md](step-ordering.md)).

## Durante a gravação

Os detalhes continuam consultáveis. **Salvar** é recusado com o motivo ("Esta automação está sendo gravada…"). Ver
[known-limitations.md](known-limitations.md).

## Evidência

- Domínio: `AutomationEditingTest.editarEtapa` e `editarSessao`.
- Aplicação: `EditorStudioTest.editarEtapa`, `parametros`, `opcao`, `localizadores`, `procedenciaERestauracao`,
  `comentarios` e `semAlteracaoParcial`; `StudioFlowTest.detalhes`, que continua passando com o editor novo.
- Homologação real, cenários 4, 5, 6 e 9.
