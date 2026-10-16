# STUDIO-006 — Recuperação após falhas

**Projeto salvo** = arquivo `.nekkora` confirmado pelo usuário. **Recuperação** = fotografia temporária das alterações
ainda não salvas. Uma nunca substitui a outra (D-S006-14).

## Consentimento informado (§23)

- Desativada por padrão. Nenhum arquivo de recuperação é criado sem consentimento.
- Arquivo → **Recuperação automática…** abre o diálogo com: o que é guardado, **onde** (caminho completo da pasta),
  **retenção** (até 3 cópias por projeto, 7 dias), que os arquivos **não são criptografados** e têm o mesmo conteúdo do
  projeto, e que valores digitados e segredos não são guardados. Opções: ativar, intervalo (1, 2 ou 5 min; padrão 2) e
  "Apagar agora os arquivos de recuperação guardados".
- A decisão fica em `settings.json` (consentimento `NOT_ASKED` / `GRANTED` / `DECLINED`) no diretório de dados.
- A barra de status mostra "Recuperação ativa" / "Recuperação desativada".

## Diretório de dados do Studio

Próprio do Studio, separado do armazenamento da Chrome Extension. Ordem de resolução:

1. propriedade Java `nekkora.studio.dataDir`;
2. variável **`NEKKORA_STUDIO_DATA_DIR`** (testes e homologação isolados da instalação pessoal);
3. Windows: `%LOCALAPPDATA%\Nekkora\Studio\data`; Linux: `$XDG_DATA_HOME/nekkora-studio` ou
   `~/.local/share/nekkora-studio`.

Fotografias em `<dados>/recovery/<automationId>/<instante>-g<geração>.nekkora`. Os testes automatizados usam
`StudioEnvironment.inMemory()` (sem diretório: recuperação indisponível) ou um diretório temporário.

## Quando uma fotografia é gravada

Só com consentimento, para cada automação com alterações **ainda não fotografadas**:

- a cada intervalo configurado (temporizador Swing);
- quando a janela do Studio perde o foco (ponto relevante do ciclo de vida).

Não fotografa durante uma gravação sem quiescência, durante um salvamento nem durante um recarregamento. Depois que o
usuário escolhe **Descartar alterações**, as alterações descartadas não são fotografadas de novo (só alterações feitas
depois da decisão). A fotografia usa o **mesmo formato
validado** (`kind=RECOVERY`, com `recoveryOf` = arquivo de origem e o tamanho/SHA-256 dele naquele momento), a mesma
gravação atômica e a mesma releitura completa. Mantém só as 3 mais recentes por automação.

## Ao abrir o Studio (§24)

O Studio varre a pasta **fora da EDT**. Para cada automação, oferece a fotografia mais recente que passa por **todas**
as verificações (as inválidas não são oferecidas e ficam até vencer). O diálogo "Recuperação disponível":

```text
Encontramos alterações não salvas.

Projeto:
Login homologacao 006

Última recuperação:
16/10/2026 — 12:18

Arquivo do projeto: recuperacao.nekkora
Recuperar não altera esse arquivo; salve depois para confirmar.

[Cancelar] [Ignorar] [Recuperar alterações]
```

| Escolha | Efeito |
|---|---|
| Recuperar alterações | a automação abre com estado **"Recuperado — alterações não salvas"** e `*`; o arquivo original não é tocado |
| Ignorar | nada é aberto; a fotografia fica (não é apagada sem salvamento ou descarte autorizado) |
| Cancelar | encerra a oferta (as demais não são mostradas agora) |

Uma automação já aberta não é oferecida. Nada é restaurado em silêncio.

**Fotografia superada** (revisão 1): se o arquivo de origem existe, é um projeto válido da mesma automação, não é o
conteúdo-base da fotografia e foi salvo **estritamente depois** dela, a fotografia (e as anteriores a ela) é apagada e
**não é oferecida**: uma recuperação antiga nunca é apresentada como mais recente que o estado confirmado. Na dúvida
(origem desconhecida, ilegível, de outra automação ou com o mesmo instante), ela continua oferecida — esconder
alterações mais novas seria pior. Teste: `ProjectRecoveryTest.superadaPorSalvamentoConfirmado`.

## Depois de recuperar

- Continua **não salvo** até o usuário salvar.
- Salvar grava no arquivo de origem **somente se o conteúdo dele ainda for o da base** registrada na fotografia; se outro
  programa alterou o arquivo depois da queda, é conflito (Cancelar / Salvar com outro nome / Recarregar).
- Projeto que nunca tinha sido salvo: Salvar pede o destino.

## Quando as fotografias são apagadas

| Evento | Apaga | Quando o resultado é informado |
|---|---|---|
| salvamento confirmado | as fotografias daquela automação até a geração gravada | no mesmo trabalho do worker, **antes** de "Projeto salvo"; se falhar, o aviso diz que uma fotografia antiga não pôde ser apagada e o Studio tenta de novo ao sair |
| Sair → Descartar alterações | as fotografias das automações descartadas | o Studio **só encerra depois** de apagá-las (ver abaixo) |
| Fechar projeto → Descartar / Recarregar do arquivo | as fotografias da automação | depois do trabalho de disco em andamento; falha = aviso e nova tentativa ao sair |
| "Apagar agora…" no diálogo | todas | falha = aviso |
| varredura ao abrir | as superadas por um salvamento confirmado posterior | não são oferecidas |
| retenção | fotografias com mais de 7 dias, na varredura | — |
| mais de 3 por automação | as mais antigas | — |

As exclusões passam pelas `FileOps` do repositório e **informam o resultado**: `discard`, `discardAll` e `clear` devolvem
`true` só se nenhuma fotografia visada restou.

## Encerramento (revisão 1, P1-B)

Sair nunca encerra a JVM com trabalho de disco pendente:

1. com alterações não salvas, o usuário escolhe Salvar todos / Descartar / Cancelar;
2. o Studio espera — **sem bloquear a EDT** — todo trabalho de disco registrado (salvamentos, fotografias em gravação,
   limpezas) e então apaga, no worker, as fotografias devidas (descartadas, ou superadas por salvamentos cuja limpeza
   tinha falhado);
3. enquanto espera, mostra "Encerrando o Studio"; um segundo pedido de Sair não abre outra pergunta;
4. só então a aplicação continua o encerramento (guarda da gravação técnica, Bridge, `System.exit`);
5. se alguma exclusão falhar, o Studio **não** afirma que limpou: pergunta "Fotografias de recuperação não apagadas —
   Sair mesmo assim?", explicando que elas poderão ser oferecidas na próxima abertura. Cancelar mantém o Studio aberto
   com as alterações.

Testes com barreira controlada (sem depender de `Thread.sleep`): `ProjectStudioTest.p1bDescartarESair` (uma fotografia
retida no worker quando o usuário pede Sair; o disco é copiado **no instante** em que o Studio encerraria e outro Studio
é aberto sobre essa cópia — nada é oferecido), `p1bSalvarESairImediatamente` (a fotografia de outra automação retém o
worker logo depois do salvamento; a fotografia anterior ao salvamento já não existe quando o sucesso é informado) e
`p1bFalhaAoApagarRecuperacao`.

## Homologação real (cenário 5)

Processo filho com o Studio real: abriu o projeto, consentimento pelo diálogo (intervalo de 1 min), alteração sem
salvar, fotografia gravada **pelo temporizador** 59 s depois; o processo foi **morto à força** (`destroyForcibly`,
sem ganchos de saída). Verificado: arquivo do projeto com o mesmo SHA-256; uma fotografia na pasta própria, sem os
valores digitados. Novo processo: oferta mostrada antes de qualquer restauração; "Recuperar alterações" → estado
recuperado não salvo, alterações presentes, arquivo intacto; Ctrl+S gravou no arquivo de origem; fotografias apagadas.
Capturas `S006-12` a `S006-16`.

Testes: `ProjectStudioTest` S006-061/062/063/064, `ProjectRecoveryTest`.
