# STUDIO-006 — Plano de testes e matriz S006

Camadas: **domínio** (`AutomationRestorationTest`), **persistência** (`ProjectRoundTripTest`, `ProjectValidationTest`,
`AtomicSaveTest`, `ProjectRecoveryTest`, `ProjectFormatContractTest`), **aplicação/interface** (`ProjectStudioTest`,
`RecorderStepsStudioTest`, `ProjectPerformanceTest`, `LayoutRegressionTest`) e **integração real** (homologação
`Studio006Homologation` + `Studio006RecoveryProbe`: Studio, Chrome for Testing, extensão, Native Host, mouse e teclado
do sistema). Os testes de round-trip comparam o **modelo estruturado** (`ModelDump`: IDs, ordem, valores, procedência,
elementos, parâmetros, comentários, evidências, revisões manuais e instantes), nunca só nomes na tela (§34).

Legenda: **A** automatizado (roda em `./mvnw verify`), **H** homologação real no Windows, **R** regressão completa.

| Caso | Verificação | Onde | Tipo |
|---|---|---|---|
| S006-001 | Baseline Studio | `./mvnw clean verify` em `3dc6035`: 344/344 | R |
| S006-002 | Novo projeto | `ProjectStudioTest`; homologação (status e `*`) | A H |
| S006-003 | Salvar projeto vazio | `ProjectRoundTripTest` | A |
| S006-004 | Salvar projeto com uma sessão | `ProjectRoundTripTest` | A |
| S006-005 | Salvar múltiplas sessões | `ProjectRoundTripTest` (005…013) | A |
| S006-006 | Preservar ordem das sessões | `ProjectRoundTripTest` | A |
| S006-007 | Preservar ordem das etapas | `ProjectRoundTripTest` | A |
| S006-008 | Preservar AutomationId | `ProjectRoundTripTest`; homologação | A H |
| S006-009 | Preservar SessionId | `ProjectRoundTripTest` | A |
| S006-010 | Preservar StepId | `ProjectRoundTripTest`; homologação (leitura independente e reabertura) | A H |
| S006-011 | Preservar nome e descrição | `ProjectRoundTripTest` | A |
| S006-012 | Preservar estado da automação | `ProjectRoundTripTest` | A |
| S006-013 | Preservar timestamps | `ProjectRoundTripTest`, `AutomationRestorationTest` | A |
| S006-014 | Preservar StepProvenance | `ProjectRoundTripTest` (014…017); homologação (RECORDED e procedência iguais em outro processo) | A H |
| S006-015 | Preservar CapturedBaseline | `ProjectRoundTripTest`; homologação (nome capturado intacto) | A H |
| S006-016 | Preservar CapturedElement | `ProjectRoundTripTest`; `RecorderStepsStudioTest` S006-060/079 | A |
| S006-017 | Preservar ElementReference efetivo | `ProjectRoundTripTest` | A |
| S006-018 | Preservar parâmetros | `ProjectRoundTripTest` (018…020); homologação (variável) | A H |
| S006-019 | Preservar opção configurada | `ProjectRoundTripTest` | A |
| S006-020 | Preservar referências de credencial | `ProjectRoundTripTest` (só o apelido) | A |
| S006-021 | Preservar localizadores | `ProjectRoundTripTest` (021…023) | A |
| S006-022 | Preservar localizador preferencial | `ProjectRoundTripTest`; fixture `preferencial-inexistente` | A |
| S006-023 | Preservar localizadores manuais não verificados | `ProjectRoundTripTest`; `ProjectValidationTest` (arquivo não declara verificado); fixture `localizador-manual-verificado`; homologação | A H |
| S006-024 | Preservar comentários | `ProjectRoundTripTest` (024…026); homologação | A H |
| S006-025 | Preservar edição manual | `ProjectRoundTripTest`; homologação | A H |
| S006-026 | Preservar derivedFrom | `ProjectRoundTripTest` | A |
| S006-027 | Preservar evidências | `ProjectRoundTripTest` (027…031, 033); homologação | A H |
| S006-028 | Preservar EvidenceId | `ProjectRoundTripTest`; homologação | A H |
| S006-029 | Preservar ordem técnica das evidências | `ProjectRoundTripTest`; homologação | A H |
| S006-030 | Preservar evidências não atribuídas | `ProjectRoundTripTest` | A |
| S006-031 | Preservar NOT_VALIDATED | `ProjectRoundTripTest`; homologação (mensagem de erro real) | A H |
| S006-032 | Não recriar etapas | `ProjectRoundTripTest` (032/034); homologação | A H |
| S006-033 | Não duplicar evidências | `ProjectRoundTripTest`; `ProjectValidationTest` S006-050; homologação | A H |
| S006-034 | Restaurar original após carregar | `ProjectRoundTripTest` | A |
| S006-035 | Undo/Redo reinicia vazio | `ProjectStudioTest` (035/036); homologação | A H |
| S006-036 | Undo funciona após nova edição | `ProjectStudioTest`; homologação (Ctrl+Z/Ctrl+Y reais) | A H |
| S006-037 | Evidência não entra no Undo | `ProjectStudioTest` (056/037) | A |
| S006-038 | Salvar | `ProjectStudioTest`, `AtomicSaveTest`; homologação | A H |
| S006-039 | Salvar como | `ProjectStudioTest`, `AtomicSaveTest`; homologação (diálogo de arquivos real) | A H |
| S006-040 | Sobrescrita confirmada | `ProjectStudioTest`, `AtomicSaveTest` | A |
| S006-041 | Arquivo alterado externamente | `ProjectStudioTest` (2), `AtomicSaveTest`; homologação (outro programa regravou) | A H |
| S006-042 | Falha de escrita | `ProjectStudioTest`, `AtomicSaveTest`; homologação (somente leitura, arquivo em uso) | A H |
| S006-043 | Disco cheio | `ProjectStudioTest`, `AtomicSaveTest` (falha injetada em `FileOps`) | A |
| S006-044 | Substituição atômica | `AtomicSaveTest` (038/044, 044) | A |
| S006-045 | Recuperação após falha de substituição | `AtomicSaveTest` (cópia de segurança e restauração) | A |
| S006-046 | Manifesto inválido | `ProjectValidationTest`; fixtures | A |
| S006-047 | Checksum incompatível | `ProjectValidationTest`; fixture `conteudo-alterado-sem-manifesto` | A |
| S006-048 | JSON inválido | `ProjectValidationTest` (JSON, UTF-8, chave duplicada) | A |
| S006-049 | Versão desconhecida | `ProjectValidationTest`; fixture `formato-mais-novo` | A |
| S006-050 | Identidade duplicada | `ProjectValidationTest`, `AutomationRestorationTest` | A |
| S006-051 | Referência inexistente | `ProjectValidationTest`; fixtures `etapa-sem-sessao`, `preferencial-inexistente` | A |
| S006-052 | ZIP malicioso | `ProjectValidationTest`; fixtures `entrada-script`, `entrada-fora-do-pacote`; homologação (`com-script.nekkora`) | A H |
| S006-053 | Limite de tamanho | `ProjectValidationTest` (bomba de descompressão, arquivo grande) | A |
| S006-054 | Carregamento integral ou recusado | `ProjectValidationTest`, `ProjectStudioTest`; homologação (nada carregado nas recusas) | A H |
| S006-055 | Estado não salvo por automação | `ProjectStudioTest` (A salvo, B alterado, C salvo); homologação | A H |
| S006-056 | Evidência posterior marca não salvo | `ProjectStudioTest` | A |
| S006-057 | Alteração durante salvamento | `ProjectStudioTest` (057/058/059, gravação lenta injetada) | A |
| S006-058 | Salvamento assíncrono | `ProjectStudioTest` | A |
| S006-059 | EDT não bloqueada | `ProjectStudioTest`; `ProjectPerformanceTest`; homologação (5 ms de EDT) | A H |
| S006-060 | Gravação ativa impede salvamento definitivo | `ProjectStudioTest`, `RecorderStepsStudioTest` (2); homologação (Ctrl+S durante gravação real) | A H |
| S006-061 | Recuperação temporária | `ProjectStudioTest` (061…064), `ProjectRecoveryTest`; homologação (consentimento pelo diálogo, temporizador) | A H |
| S006-062 | Recuperação após encerramento inesperado | `ProjectStudioTest`; homologação (processo morto à força, oferta, recuperar, salvar) | A H |
| S006-063 | Recuperação não sobrescreve projeto | `ProjectStudioTest`; homologação (SHA-256 igual) | A H |
| S006-064 | Descarte de recuperação | `ProjectStudioTest`, `ProjectRecoveryTest`; homologação (apagadas após salvar) | A H |
| S006-065 | Projetos recentes | `ProjectStudioTest`; homologação (reaberto pelo submenu) | A H |
| S006-066 | Projeto aberto duas vezes | `ProjectStudioTest` | A |
| S006-067 | Projeto com mesma identidade em arquivos distintos | `ProjectStudioTest` | A |
| S006-068 | Fechamento com alterações | `ProjectStudioTest` (068/069), §29; homologação | A H |
| S006-069 | Salvar todos | `ProjectStudioTest` (068/069, 069/070); homologação (falha não encerra; sucesso encerra) | A H |
| S006-070 | Cancelar encerramento | `ProjectStudioTest`; homologação | A H |
| S006-071 | Privacidade v2 | `ProjectValidationTest`; fixture `dado-da-pagina-no-elemento`; homologação | A H |
| S006-072 | Política antiga incompatível | `ProjectValidationTest`; fixture; homologação (`politica-v1.nekkora`) | A H |
| S006-073 | Nenhum segredo de credencial | `ProjectValidationTest`; homologação (usuário e senha digitados ausentes do arquivo e da recuperação) | A H |
| S006-074 | Nenhum dado bruto em logs | `ProjectStudioTest`; homologação (logs reais conferidos) | A H |
| S006-075 | Quatro temas | `ProjectStudioTest` (075/076); `GoldenScreenshotTest` | A |
| S006-076 | Acessibilidade | `ProjectStudioTest` (nomes acessíveis, atalhos sem conflito); `LayoutRegressionTest` (barra de status a 150%) | A |
| S006-077 | Golden Screenshots | `GoldenScreenshotTest` Windows e Linux, imagens **não regeneradas** | R |
| S006-078 | Regressão STUDIO-001 a 005 | `./mvnw clean verify` Windows e `tools/golden-linux.sh verify` | R |
| S006-079 | Integração com extensão | `RecorderStepsStudioTest` S006-060/079; homologação (gravação real, salvar, reabrir) | A H |
| S006-080 | Nenhuma execução de automação ao carregar | `ProjectStudioTest`; homologação (Recorder livre, SHA igual) | A H |

## Contrato do formato

`ProjectFormatContractTest`: schema 1.0.0 igual ao código (vocabulários e constantes); fixtures válidas congeladas
abertas pelo Studio e reescritas identicamente; o que o Studio escreve hoje é conforme ao schema; 18 fixtures inválidas
recusadas com o erro esperado (o schema recusa as estruturais; as semânticas são declaradas como tais); o validador de
schema do teste recusa de fato.

## Dois ciclos (§36)

`ProjectRoundTripTest` "dois ciclos": automação do §36 (três sessões, gravadas e manuais, parâmetros, comentários,
localizadores, revisões, evidências atribuídas e não atribuídas, duplicada, movida) → salvar → nova leitura (novo
agregado) → comparar → editar de novo → salvar → reabrir → comparar. A "nova instância do Studio" com processo
separado foi feita na homologação (o processo filho abre o arquivo gravado pelo Studio principal já encerrado).

## Como rodar

```bash
NEKKORA_BRIDGE_DIR=<pasta isolada> ./mvnw clean verify     # Windows
timeout 2400 sh tools/golden-linux.sh verify                # Linux de referência (Docker)
# homologação (Windows, com a extensão ao lado, Native Host compilado e Chrome for Testing em .tools):
NEKKORA_STUDIO_DATA_DIR=<pasta> NEKKORA_BRIDGE_DIR=<pasta> java -cp <classpath de teste do app> \
  com.nekkora.studio.app.evidence.Studio006Homologation <saída> <repositório da extensão> <classpath>
```
