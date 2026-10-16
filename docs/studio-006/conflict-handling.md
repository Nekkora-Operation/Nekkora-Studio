# STUDIO-006 — Conflitos

## Alteração externa (§19)

O Studio guarda a impressão digital (tamanho + SHA-256) do arquivo na última leitura ou gravação. Antes de substituir
— e de novo imediatamente antes da troca — confere se o arquivo no disco ainda é aquele. Se não for (alterado,
substituído ou removido por outro programa), **nada é sobrescrito** e o diálogo "Arquivo alterado fora do Studio"
oferece:

| Opção | Efeito |
|---|---|
| Cancelar | nada acontece; as alterações continuam abertas; estado "Arquivo alterado externamente" |
| Salvar com outro nome | "Salvar como…": as duas versões ficam preservadas; o projeto passa a usar o novo arquivo |
| Recarregar do arquivo | pede confirmação ("as alterações deste projeto que existem só no Studio serão descartadas") e abre o conteúdo atual do arquivo |

Não há merge automático. A leitura também confere que o arquivo não mudou **durante** a leitura (`CONFLICT`).

## Salvar como sobre um arquivo existente

Pede confirmação "Substituir arquivo?" (perigo). Sem confirmação, nada é gravado. Um arquivo que já é o projeto aberto
de outra automação é recusado ("Arquivo em uso por outro projeto").

## Mesmo projeto aberto duas vezes (§26)

- Mesmo caminho: ativa a automação existente; nenhuma segunda instância com as mesmas identidades.
- Outro arquivo com o mesmo `AutomationId`: "Projeto com a mesma identidade já aberto" — nada substituído.
- Nome de automação igual ao de outra aberta: o domínio recusa (nomes únicos no catálogo) e a mensagem pede para
  renomear ou fechar a outra.

## Gravação × salvamento (§22)

Sem quiescência do Recorder para aquela automação, Salvar, Salvar como e Fechar projeto são recusados com a mensagem
da missão; a gravação não é finalizada nem cancelada. Quiescência = estado `COMPLETED`/`CANCELLED`/`IDLE` (ou outra
automação) **e** nenhuma etapa ou evidência pendente ou aguardando entrega. Desconexão do navegador (`DEGRADED`) não
conta como encerramento. Teste: `RecorderStepsStudioTest.s006060Quiescencia`.

## Salvamento em andamento

Um segundo Salvar da mesma automação durante a gravação do primeiro é recusado ("Salvamento em andamento"). Edições
continuam permitidas; a geração editada durante o salvamento continua pendente depois dele.

Testes: `AtomicSaveTest` S006-041; `ProjectStudioTest` S006-040, S006-041 (dois), S006-066, S006-067;
homologação cenário 4.
