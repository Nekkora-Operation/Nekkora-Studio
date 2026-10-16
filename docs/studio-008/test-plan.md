# STUDIO-008 — Plano de testes

| Camada | Classe | O que confere |
|---|---|---|
| Domínio | `VariableDomainTest` (11) | declarar, nomes, significado × tipo × sensibilidade, restrições, constantes canônicas e sensíveis, escopo e produtora, renomear (original e procedência intactos), excluir com/sem vínculos, histórico pelo `Snapshot`, editar, reconstrução |
| Persistência | `ProjectFormat11ContractTest` (7) | schema 1.1.0 = domínio e = schema 1.0.0 no resto; fixtures válidas 1.1.0 (projeto e recuperação) relidas e reescritas idênticas; escrita 1.1.0 conforme; **sem variáveis = 1.0.0 congelado, entrada por entrada**; 1.0→1.1→1.0 conforme o conteúdo; cópia de segurança (conflito, nunca substitui); 17 fixtures inválidas |
| Persistência | `ProjectValidationTest`, `ProjectFormatContractTest` | ajustes autorizados 1 e 2 (1.2.0 = mais novo); contrato 1.0.0 intacto |
| Validação | `ValidationRules11Test` (8) | regras 1.0.0 intactas; não declarada; entrada; escopo/tipo/origem; saída de etapa antes/depois/própria/ciclo; referências quebradas; apontamentos da variável e ordem; credencial e campo sensível |
| Validação | `ValidationContract11Test` (4) | schema 1.1.0 = catálogo completo; fixture 1.1.0 congelada; cobertura; privacidade |
| Validação | `ValidationContractTest` | ajuste autorizado 3 (schema 1.0.0 × subconjunto 1.0.0, códigos novos só no fim); fixture 1.0.0 idêntica |
| Semântica | `SemanticContractTest` (6) | schema = código; fixture congelada; determinismo; conteúdo (uso derivado, produtora, incertezas); privacidade; separado do relatório e só leitura; independência (sem UI, disco, rede, relógio) |
| App | `VariableStudioTest` (3) | operações com histórico e não salvo; migração com confirmação e cópia byte a byte; cópia nunca substituída e "Salvar como" sem migrar |
| App | `VariablesUiTest` (8) | painel; declarar CPF; constante sensível recusada no campo; editor de etapa; renomear com prévia + Undo/Redo; excluir confirmado; apontamento → variável; 4 temas e acessibilidade; bloqueio de gravação |
| App | `ValidationStudioTest` | ajuste autorizado 4 (`VARIABLE_NOT_DECLARED`) |
| App | `Variables150Test` | execução `scale-150` |
| App | `VariablesPerformanceTest` | 10/100/1.000/5.000 etapas (`performance.md`) |
| Homologação | `Studio008Homologation` | Windows real, Robot, cenários 1–8 (`homologation.md`) |

Regressão: Windows `./mvnw clean verify` e Linux `tools/golden-linux.sh verify` (contêiner com JDK fixado).
