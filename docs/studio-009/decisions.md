# STUDIO-009 — Decisões

| Id | Decisão | Motivo |
|---|---|---|
| D-S009-01 | O plano do debug é uma **projeção** de `Automation`/`AutomationStep`/variáveis/validação/contrato semântico, identificada por `StepId`. Não é um segundo modelo de automação | §1.1 da missão; o modelo atual já tem intenção, ação, parâmetros, alvo e procedência |
| D-S009-02 | A extensão é a única autoridade sobre identidade web. O Studio não reimplementa `TargetResolver`, só pede e apresenta | §7; evita duas inteligências divergentes |
| D-S009-03 | Resolução de debug **somente leitura** por `targetId` na aba da sessão de debug: não atualiza o registro nem transfere o dono. Sem `targetId` utilizável, o **descritor recuperável** (o `ElementReference` persistido) gera uma identidade transitória para o mesmo resolvedor, com `IDENTITY_SOURCE=DESCRIPTOR` | G0 §2: `TARGET_NOT_OWNED` após reinício e registro só em laboratório e local |
| D-S009-04 | Debug entra no `nekkora-bridge/1` como capacidade `guided-debug/1`, com comandos explícitos no schema e na lista do host. Sem relay genérico nem execução de JavaScript arbitrário | §4; mesmo caminho de `steps/1` e `evidence/1` |
| D-S009-05 | Saída do debug pelo host só em status, códigos, contagens e referência compacta filtrada (`DebugPrivacy`). As frases livres do resolvedor não atravessam o Bridge | `nekkora-privacy/2`; as frases podem citar valores da assinatura |
| D-S009-06 | `DecisionActor` ∈ {`DEVELOPER`, `TEST_AGENT`}. `TEST_AGENT` só em modo de laboratório explícito, visível na interface. Sem rótulos de confirmação humana. `TEST_AGENT` nunca sobrescreve `DEVELOPER` | §0.1 |
| D-S009-07 | Confirmação (durável, com ator) separada da verificação técnica (situada no tempo, no contexto e na fonte de identidade). Persistência em `.nekkora` 1.2.0; 1.0.0 e 1.1.0 continuam congelados | §1.3 e §4 |
| D-S009-08 | Sonda de interação só em origem LAB, com valores por **chave** de um catálogo sintético empacotado. O valor nunca atravessa o Bridge nem vai a logs | §2 e §7; não contornar a privacidade |
