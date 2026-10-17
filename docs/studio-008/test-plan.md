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
| Revisão 1 | `SemanticContractApiTest` (3) | P1: nenhum caminho público do contrato alcança valor fixo, finalidade, literais, apelidos ou nomes; sem tipos do domínio além de identidades e enums; origem só `kind`/`constantDeclared`/`producer` |
| Revisão 1 | `VariableRepresentationTest` (4), `SemanticContractTest#representacao`, `ProjectFormat11ContractTest#cpfComMascara` | P2: CPF sem formato → aviso (confirmado e proposto); só dígitos/máscara → sem aviso; valor continua não disponível; outros significados e regras 1.0.0 sem o código; incerteza no contrato; máscara gravada e relida no 1.1.0 |
| App | `VariableStudioTest` (3) | operações com histórico e não salvo; migração com confirmação e cópia byte a byte; cópia nunca substituída e "Salvar como" sem migrar |
| App | `VariablesUiTest` (8) | painel; declarar CPF; constante sensível recusada no campo; editor de etapa; renomear com prévia + Undo/Redo; excluir confirmado; apontamento → variável; 4 temas e acessibilidade; bloqueio de gravação |
| App | `ValidationStudioTest` | ajuste autorizado 4 (`VARIABLE_NOT_DECLARED`) |
| App | `Variables150Test` | execução `scale-150` |
| App | `VariablesPerformanceTest` | 10/100/1.000/5.000 etapas (`performance.md`) |
| Homologação | `Studio008Homologation` | Windows real, Robot, cenários 1–8 (`homologation.md`) |
| Homologação | `Studio008Review1Homologation` | revisão 1: formato, aviso, contrato, Undo/Redo e gravação (`review-1.md`) |
| Revisão 2 | `VariableDomainTest#formatoCoerenteComSignificado`, fixture inválida 1.1.0 `formato-incoerente-com-significado.json`, `VariablesUiTest#formatoIncoerenteRecusado` | matriz significado × formato contra formas válidas fictícias (8 recusas: confirmado e proposto), edição recusada sem alterar a variável; arquivo 1.1.0 com CPF em forma de e-mail recusado; formulário real com a mensagem no campo de formato |
| Homologação | `Studio008Review2Homologation` | revisão 2: recusa no formulário real (criar e editar), formato coerente aceito, validação limpa (`review-2.md`) |

Regressão: Windows `./mvnw clean verify` e Linux `tools/golden-linux.sh verify` (contêiner com JDK fixado).
