# Decisões — NEKKORA-SPEC-OPTIONAL-COLUMNS-001

Decisões técnicas reversíveis (D-OC-xx) e o que fica para a titular. Nada do Python gerado foi editado à mão.

| ID | Decisão | Motivo | Reversível como |
|---|---|---|---|
| D-OC-01 | A regra mora na **especificação funcional** (`OUTPUT_REQUIREMENT` por etapa Observar), não no rascunho do PDD nem em `VariableConstraints.required` | é onde as colunas já são decididas (cabeçalho, fonte alternativa) e o que o PDD mostra no apêndice "Dados do processo"; muda a `specRevision`, então o gate e o código anteriores deixam de valer sozinhos. `VariableConstraints.required` existe no modelo mas nunca foi revisado como contrato de saída | migrar para um campo do PDD quando ele tiver dicionário de dados |
| D-OC-02 | Padrão `REQUIRED`; `OPTIONAL` só por decisão revisada com motivo de vocabulário fechado (`ABSENT_FOR_SOME_ITEMS`, `NOT_APPLICABLE_FOR_SOME_ITEMS`) | nada inferido do tipo, do nome ("Morte"), de uma execução ou da IA; o motivo é a justificativa sem texto livre (privacidade) | acrescentar motivos ao vocabulário (nova versão) |
| D-OC-03 | A revisão aparece na janela **Desenvolver automação → Pendências** como INFORMAÇÃO por coluna (não bloqueia, não some depois de decidida: dá para rever). A coluna-chave não aparece (sempre obrigatória) | é o fluxo onde a pessoa já responde as decisões de coluna; INFORMAÇÃO não muda o estado do gate de projetos antigos | — |
| D-OC-04 | Decisão presa ao **fingerprint** da etapa (como as outras decisões da especificação) | alvo trocado → a decisão não vale; a coluna volta a obrigatória até nova revisão | — |
| D-OC-05 | Coluna opcional → leitura com `exists()` (sem espera) DEPOIS da página pronta (1ª leitura obrigatória com alvo da tela); tela só com leituras opcionais é recusada no gerador (`OPTIONAL_READ_WITHOUT_REQUIRED_ANCHOR`) | "não existe" só é conclusivo com a página carregada; sem âncora, ausência poderia ser lentidão | — |
| D-OC-06 | `_date_if_present`: elemento presente SEM texto (vazio/só espaços) também é ausência; texto presente que não é data completa continua `DATA_INCOMPLETA_OU_AMBIGUA` | regra da missão: só vazio/espaços é ausência; dado malformado nunca vira `None`. O helper só é emitido quando há data opcional ou data com fonte alternativa (nenhuma amostra anterior o usa) | — |
| D-OC-07 | A linha da planilha exige só `REQUIRED_FIELDS`; coluna opcional sem valor fica `None` → `OutputColumn(..., required=False)` → célula não escrita | é a semântica real do nekkora-rpa 0.3.0 (`xlsx.py:185-210`, `:293-312`): nenhum valor convencional ("", "N/A", data fictícia) | — |
| D-OC-08 | Versões só sobem quando a coluna opcional é usada: especificação 1.2.0, configuração 1.3.0, manifesto 1.5.0; sem ela, 1.1.0/1.2.0/1.4.0 e Python byte a byte iguais | compatibilidade testada; projetos e amostras anteriores não mudam | — |
| D-OC-09 | Framework **sem mudança** (0.3.0 já suporta). Catálogo do Studio sem mudança (`OutputColumn` é tipo de dado de `xlsx.schema`) | G0: nenhuma capacidade nova; fail-closed `FRAMEWORK_CATALOG_MISSING` intacto | catalogar `xlsx.output_column` se uma versão nova mudar a assinatura |
| D-OC-10 | Contrato V5 ganha `OUTPUT_REQUIREMENT_CONSISTENT` (projetos com planilha): o total de verificações desses projetos passa de 11 para 12 | divergência manifesto × `main.py` × processo é bloqueio objetivo | — |
| D-OC-11 | Emissor tabular (RPA Challenge) fora do alcance | não tem planilha de saída por colunas | — |

## Achados

| ID | Achado | Estado |
|---|---|---|
| F-OC-01 | Na homologação real, o "Nome completo" da pessoa viva saiu com a chamada de nota visível da página (texto do tipo `[nota 1]`). O leitor do framework (`read_items`) devolve o texto VISÍVEL da célula, e a chamada de nota faz parte dele; o conferidor independente a remove (considera que não é dado). Comportamento anterior a esta missão (os quatro artigos da CQ-001 não tinham nota nessas células) e sem relação com a opcionalidade | **decisão da titular**: remover chamadas de nota é transformar o texto lido (contrato de dado) — seria regra explícita do PDD/gerador ou do framework, em missão própria; nada foi alterado para "passar" |
| F-OC-02 | O orquestrador levava à janela só os AVISOS do gate: as informações de obrigatoriedade não apareciam em Pendências (achado na 1ª execução pela interface) | corrigido no `6828edc`, com teste |

## Extensões futuras (documentadas, não feitas)

- Colunas numéricas/booleanas: o framework 0.3.0 só tem `TEXT`, `ISO_DATE`, `URL`. Uma coluna `NUMBER`/`BOOL` precisará
  julgar ausência pelo tipo (0 e `False` são valores). Exige versão nova do framework e do catálogo.
- Obrigatoriedade das colunas de ENTRADA (`tabular.Column.required`) pela mesma decisão revisada.
- Dicionário de dados no próprio PDD (hoje o PDD mostra o apêndice gerado da especificação).

## Pendências da titular

1. Aceite humano desta mudança funcional (G7): não reutiliza o aceite da CODEGEN-QUALITY-001.
2. F-OC-01: manter o texto visível como está ou remover chamadas de nota (em missão própria).
3. Publicação/push: continua bloqueado (repositórios públicos; código proprietário).
