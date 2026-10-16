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

Não fotografa durante uma gravação sem quiescência nem durante um salvamento. A fotografia usa o **mesmo formato
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

## Depois de recuperar

- Continua **não salvo** até o usuário salvar.
- Salvar grava no arquivo de origem **somente se o conteúdo dele ainda for o da base** registrada na fotografia; se outro
  programa alterou o arquivo depois da queda, é conflito (Cancelar / Salvar com outro nome / Recarregar).
- Projeto que nunca tinha sido salvo: Salvar pede o destino.

## Quando as fotografias são apagadas

| Evento | Apaga |
|---|---|
| salvamento confirmado | as fotografias daquela automação até a geração gravada |
| Sair → Descartar alterações / Fechar projeto → Descartar | as fotografias da automação descartada |
| "Apagar agora…" no diálogo | todas |
| retenção | fotografias com mais de 7 dias, na varredura |
| mais de 3 por automação | as mais antigas |

## Homologação real (cenário 5)

Processo filho com o Studio real: abriu o projeto, consentimento pelo diálogo (intervalo de 1 min), alteração sem
salvar, fotografia gravada **pelo temporizador** 59 s depois; o processo foi **morto à força** (`destroyForcibly`,
sem ganchos de saída). Verificado: arquivo do projeto com o mesmo SHA-256; uma fotografia na pasta própria, sem os
valores digitados. Novo processo: oferta mostrada antes de qualquer restauração; "Recuperar alterações" → estado
recuperado não salvo, alterações presentes, arquivo intacto; Ctrl+S gravou no arquivo de origem; fotografias apagadas.
Capturas `S006-12` a `S006-16`.

Testes: `ProjectStudioTest` S006-061/062/063/064, `ProjectRecoveryTest`.
