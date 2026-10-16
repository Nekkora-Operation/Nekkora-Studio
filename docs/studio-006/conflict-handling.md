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

Pede confirmação "Substituir arquivo?" (perigo). Sem confirmação, nada é gravado. A confirmação vale para o conteúdo
que existe **naquele momento** no destino (revisão 1, P2): a impressão digital do destino é lida fora da EDT antes da
pergunta e conferida de novo imediatamente antes da troca. Se outro programa alterar, substituir ou remover o arquivo
depois da confirmação, é conflito — nada é sobrescrito, a mensagem diz que o arquivo mudou depois da confirmação e as
alterações continuam abertas. Teste: `ProjectStudioTest.p2SobrescritaAlteradaDepoisDaConfirmacao` (alteração externa
feita enquanto o temporário é gravado, entre a confirmação e a troca). Um arquivo que já é o projeto aberto
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

## Recarregar do arquivo (revisão 1, P1-A)

Transacional do ponto de vista do workspace:

1. não começa durante um salvamento ou outro recarregamento do mesmo projeto (nem pergunta nada), nem durante uma
   gravação sem quiescência;
2. o arquivo é lido e validado no worker (mesma leitura integral da abertura, exigindo a mesma `AutomationId`);
   enquanto isso, Salvar, Salvar como, Fechar projeto e um novo Recarregar daquele projeto são recusados ("em andamento");
3. na EDT, a substituta precisa ser **admitida** pelo catálogo — `AutomationCatalog.replace` confere identidade e nome
   único **antes** de trocar e troca na mesma posição;
4. só depois disso o estado anterior é descartado (aba reaberta, histórico vazio, estado "Salvo", fotografias de
   recuperação das alterações descartadas apagadas).

Qualquer recusa (arquivo inválido, outra automação, nome em uso por outro projeto aberto, gravação iniciada durante a
leitura, projeto fechado) deixa automação, alterações não salvas, seleção, histórico, arquivo associado e recuperação
**exatamente como estavam**, com o aviso "Não foi possível recarregar … O projeto aberto não mudou". Testes:
`ProjectStudioTest.p1aRecarregarRecusadoPreservaWorkspace` (dois projetos abertos; o arquivo de A passa a ter o nome de
B) e `p1aRecarregarESalvarNaoSeSobrepoem`; domínio: `AutomationRestorationTest.substituirNoCatalogo`.

Testes: `AtomicSaveTest` S006-041; `ProjectStudioTest` S006-040, S006-041 (dois), S006-066, S006-067, `p1a*`, `p2*`;
homologação cenário 4.
