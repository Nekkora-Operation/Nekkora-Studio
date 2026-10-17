# STUDIO-008 — Homologação real (Windows)

Roteiro: `nekkora-studio-app/src/test/java/com/nekkora/studio/app/evidence/Studio008Homologation.java`.
Studio de verdade (janela 1366×900, dados isolados por `NEKKORA_STUDIO_DATA_DIR`), mouse e teclado do sistema (Robot),
diálogos do design system e diálogos de arquivo do sistema. Sem navegador: nenhum cenário grava, executa ou consulta
página. Dados fictícios. Capturas só do conteúdo da janela do Studio ou do diálogo (rootPane).

**Resultado: 40 verificações, 0 falhas** (Windows 11, tela 1920×1032, Java 21.0.12). Log completo:
`evidence/S008-homologacao-log.txt`.

As listas de escolha (tipo, significado, sensibilidade, escopo, origem e a lista de variáveis declaradas no editor de
etapa) foram ajustadas pela API do próprio componente; cliques, digitação, atalhos, abas, botões, menus e diálogos de
arquivo usaram o Robot.

## Cenários

| # | Cenário | Verificações | Capturas |
|---|---|---|---|
| 1 | Declarar CPF sem valor | formulário sem campo de valor de execução; CPF com valor fixo recusado no campo e nada declarado; declarado como entrada, dados pessoais, significado confirmado, sem valor; linha do painel | `S008-01-*` |
| 2 | Usar em Preencher | editor de etapa lista só as declaradas visíveis; escolher preenche a referência e mostra a declaração; validação 1.1: `INPUT_VALUE_NOT_AVAILABLE`, sem "não declarada" | `S008-02-*` |
| 3 | Fora de escopo e sem declaração | `VARIABLE_OUT_OF_SCOPE`; `VARIABLE_NOT_DECLARED` para nome parecido ("Cliente.Nome") | `S008-03-*` |
| 4 | Saída de etapa não comprovada | `STEP_OUTPUT_NOT_PROVEN` e `VARIABLE_SEMANTIC_UNCONFIRMED`; painel "(não comprovada)"; contrato semântico com `UNVERIFIED_AT_RUNTIME` e sem nomes de etapas (`evidence/S008-contrato-semantico.json`) | `S008-04-*` |
| 5 | Renomear com Desfazer/Refazer | prévia "1 etapa passa a usar o novo nome…"; nome e referência mudam juntos; Ctrl+Z volta os dois; Ctrl+Y refaz | `S008-05-*` |
| 6 | Salvar 1.1, reabrir e ler 1.0 | sem variáveis o projeto foi gravado em 1.0.0; arquivo intocado até salvar; cancelar a migração não grava nem copia; confirmar grava a cópia idêntica `cadastro-008 (formato 1.0.0).nekkora` e o projeto em 1.1.0; `variables.json` sem valores; reaberto com 2 variáveis e formato 1.1.0; cópia 1.0.0 aberta pelo leitor 1.0 | `S008-06-*` |
| 7 | Projeto antigo | o `.nekkora` 1.0.0 da STUDIO-006 aberto sem nenhum diálogo de credenciais; regras 1.1 apontam a credencial (só apelido) e a referência sem declaração; Executar indisponível; validar não altera o arquivo (sha e data de modificação) nem marca não salvo; fechado sem gravar, idêntico e ainda 1.0.0 | `S008-07-*` |
| 8 | Quatro temas e 5.000 etapas | projeto de 5.000 etapas e 1.000 variáveis: abrir (pelo diálogo de arquivo, inclui a digitação do caminho) 4,3 s; aba Variáveis 0,67 s; validar 78 ms (73 ms na EDT); renomear 7 ms na EDT; painel visível nos 4 temas | `S008-08-*` |

Privacidade: os logs da aplicação durante o roteiro (`evidence/S008-logs-da-aplicacao.txt`) têm só operação, resultado,
duração, bytes e código — nenhum nome de variável nem valor. A recuperação automática estava desativada (padrão), então
nenhuma fotografia foi gerada; a verificação de recuperação passou por não haver o que varrer — a cobertura de
recuperação com variáveis vem dos testes automatizados (a fotografia usa o mesmo codec do projeto).

## Revisão 1 do PR #11

Homologação real focada, `Studio008Review1Homologation`: **12 verificações, 0 falhas** (formato de CPF no formulário
real, aviso no painel, contrato, Ctrl+Z/Ctrl+Y, gravação 1.1.0). Detalhes e justificativa para não repetir os 40 em
`review-1.md`; evidências em `evidence/revisao-1/`.

## Revisão 2 do PR #11

Homologação real focada, `Studio008Review2Homologation`: **7 verificações, 0 falhas** (formato que contradiz o
significado recusado no formulário real, ao criar e ao editar; formato coerente aceito; validação sem inconsistência).
Detalhes e justificativa em `review-2.md`; evidências em `evidence/revisao-2/`.

## Ocorrências durante a homologação

Execuções anteriores foram interrompidas por janelas de outros programas sobre o Studio (menu Iniciar do Windows e a
janela do Nekkora Agent) e por teclas que não chegaram à JVM. O roteiro passou a: (1) nunca clicar quando outro
programa está ativo; (2) confirmar, com uma sonda inofensiva (Shift), que o teclado está com o Studio antes de cada
atalho; (3) repetir um atalho só quando o efeito esperado não apareceu. Numa dessas tentativas, antes dessas travas,
até três Ctrl+N podem ter ido para outra janela em primeiro plano. A execução registrada aqui foi feita com a máquina
livre, sem nenhuma repetição de atalho. Capturas de falha das tentativas interrompidas foram apagadas (uma mostrava a
área de trabalho) e não fazem parte das evidências.
