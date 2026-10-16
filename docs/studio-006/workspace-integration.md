# STUDIO-006 — Integração com o workspace

## `StudioState`

O booleano global `unsavedChanges` (STUDIO-001) deu lugar ao estado **por automação** (`ProjectState`, ver
[persistence-model.md](persistence-model.md)). `hasUnsavedChanges()` e `unsavedAutomations()` derivam dele. Novo evento
`StudioChange.PROJECT` (arquivo, situação, erro) — separado de `CONTENT` e `UNSAVED_CHANGES`.

## `ProjectService` (app)

Todas as operações começam na EDT (verificado) e devolvem `CompletableFuture<Outcome>` (`SAVED`, `OPENED`,
`ACTIVATED`, `CANCELLED`, `BUSY`, `RECORDING`, `FAILED`, `CONFLICT`, `CLOSED`, `RELOADED`).

```text
EDT                                  worker (1 thread, "nekkora-persistence")        EDT
fotografia (Snapshot.of) ─────────► serializar, gravar, reler, confirmar ──────────► estado, aviso, recentes
                                     ler, validar, reconstruir ────────────────────► catálogo.adopt, aba
```

O worker nunca lê o agregado mutável. Em gravação simultânea com o Recorder, o Recorder só altera a automação dele,
e uma automação em gravação não é salva (quiescência).

## Catálogo

- `AutomationCatalog.adopt(automação)`: entra inteira, depois da validação; nome e identidade únicos.
- `AutomationCatalog.remove(id)`: **Fechar projeto** retira do catálogo (com pergunta se houver alterações).
- Fechar a **aba** continua só fechando a área visual: a automação fica no catálogo (STUDIO-001, §29).

## Guarda de saída

`StudioController.requestExit` → `ProjectService.confirmExit` (Salvar todos / Descartar alterações / Cancelar) → só
então a guarda da STUDIO-002 (gravação técnica pendente). Se algum salvamento falhar, o Studio não fecha; um projeto
não conta como salvo porque outro foi salvo.

## Recorder

`RecorderPresenter.busyForSave(snapshot, automação)` diz se a automação está sem quiescência. O `ProjectService`
recebe esse predicado (`setBusyGuard`). Salvar não depende da extensão: com o navegador desconectado, salvar funciona
normalmente (o predicado só olha o estado local do Recorder).

## Linha de comando

`studio.cmd projeto.nekkora` (ou `java -jar … projeto.nekkora`) abre os arquivos depois da oferta de recuperação.
