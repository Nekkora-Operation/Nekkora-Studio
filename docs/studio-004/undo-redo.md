# STUDIO-004 — Desfazer e refazer

## Como usar

- **Ctrl+Z** desfaz e **Ctrl+Y** refaz, na automação ativa. Também há os botões **Desfazer** e **Refazer** na barra; a
  dica diz o que será desfeito ("Desfazer: Mover etapa (Ctrl+Z)").
- Dentro de um campo de texto, Ctrl+Z e Ctrl+Y desfazem e refazem a **digitação daquele campo** (`TextUndo` do Design
  System) e nunca chegam ao histórico estrutural.

## O que entra no histórico

Toda operação de edição **confirmada** pelo domínio:

- propriedades da automação e da sessão;
- nova sessão, mover e excluir sessão;
- nova etapa manual, editar, mover, duplicar e excluir etapa;
- parâmetros, localizador preferencial e propostos, comentários e restaurar o original.

Não entram:

- operações recusadas;
- operações sem efeito;
- etapas entregues pelo Recorder (`StepIntake`);
- o descarte de uma gravação cancelada.

## Como funciona

- `EditHistory` guarda, por automação, até **100** operações (`EditHistory.DEFAULT_LIMIT`; o construtor
  `EditHistory(int limit)` permite outro limite). As mais antigas saem primeiro. Tudo fica **somente em memória**.
- Cada entrada tem duas fotografias do agregado (`Automation.Snapshot`), antes e depois, mais a seleção de antes e de
  depois.
- A fotografia guarda as **próprias** sessões e etapas e o estado imutável de cada uma. Desfazer uma exclusão devolve as
  mesmas identidades, e refazer não cria identificadores. Etapas que não mudaram compartilham o mesmo estado entre
  fotografias, o que mantém o histórico leve.
- Uma nova edição depois de desfazer invalida o que poderia ser refeito.
- Desfazer restaura a seleção de antes, se ela ainda for válida; senão, seleciona a automação.

## Coerência e conflitos

Cada entrada sabe em que revisão a automação precisa estar para ser desfeita ou refeita:

- desfazer ou refazer conta uma revisão; a entrada vizinha (que terminou exatamente no estado restaurado) é reencadeada;
- se a automação mudou **por outro caminho** entre duas entradas (por exemplo, o Recorder entregou etapas), a revisão
  não bate:
  - **nada é aplicado** sobre um estado diferente;
  - o histórico da automação é descartado;
  - o usuário vê: "A automação mudou por outro caminho depois desta alteração (por exemplo, recebeu etapas do Recorder).
    O histórico de edição foi descartado…";
- um nome de automação já usado por outra é recusado ao restaurar, e o histórico continua.

Ctrl+Z nunca desfaz uma etapa que o navegador ainda está entregando: as entregas do Recorder ficam fora do histórico, e
desfazer fica **bloqueado** na automação em gravação.

## Evidência

- Aplicação: `EditorStudioTest.desfazerRefazer`, `refazer`, `conflitoComORecorder`, `semAlteracaoParcial` e
  `subirEDescerSessao` (desfazer de mover sessão); `RecorderStepsStudioTest.s004BloqueioDuranteGravacao`.
- Domínio: `AutomationEditingTest.fotografia`.
- Homologação real, cenário 7: renomear, mover e excluir; Ctrl+Z ×3, Ctrl+Y ×3 e Ctrl+Z, com o teclado real; nenhuma
  entidade perdida ou duplicada.
- Desempenho: `EditorPerformanceTest` (desfazer/refazer em milissegundos, também com 1.000 etapas).
