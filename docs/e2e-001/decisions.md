# NEKKORA-E2E-001 — Decisões

| # | Decisão | Motivo | Quem decidiu |
|---|---|---|---|
| D-E2E-01 | Origem canônica `https://rpachallenge.com` (exata), ambiente `benchmark` separado de `lab` | autorização explícita da missão; LAB continua só loopback | missão / TEST_AGENT |
| D-E2E-02 | Terceiros do site (fontes, métrica) bloqueados em todos os navegadores | não são necessários ao formulário | TEST_AGENT |
| D-E2E-03 | Candidato LABEL por `<label>` irmão único (extensão) e estratégia `label` (framework 0.2.0) | o nome ARIA dos campos é vazio e `id`/`name` são aleatórios a cada carga | TEST_AGENT |
| D-E2E-04 | Perfil de captura visual `BENCHMARK` (origem exata) para o PDD | o PDD só capturava em LAB; a política visual não foi relaxada | TEST_AGENT |
| D-E2E-05 | Gerador Python integrado ao Studio (STUDIO-011), com configuração de geração por projeto, pré-voo com bloqueios e proteção de edição manual | a missão exige código GERADO pelo Studio | TEST_AGENT |
| D-E2E-06 | Resultado lido por padrão de texto ANCORADO declarado na configuração (`read_text_matching`) | a mensagem final não tem rótulo nem identidade exportável | TEST_AGENT |
| D-E2E-07 | Vocabulário de interface +"excel" +"cloud" (revisão versionada) | o link de download só tinha localizador posicional | **usuária** (2 perguntas; a 1ª resposta foi dada sobre diagnóstico incompleto — corrigido e perguntado de novo) |
| D-E2E-08 | Na construção de TESTE, a origem exata vai no manifesto | `developerPrivate.addHostPermission` para origem opcional não expunha a URL da aba | TEST_AGENT (só teste) |
| D-E2E-09 | Descrição de localizador acima de 200 caracteres vai vazia (extensão) | o contrato do Bridge limita a 200; o host descartava a etapa inteira | TEST_AGENT |
| D-E2E-10 | Recorder: `publish()` antes do comando de sincronização de evidência | corrida real (1/8 falhas antes, 0/12 depois) | TEST_AGENT |
| D-E2E-11 | Ao salvar após o debug, aceitar a migração do projeto 1.1.0 → 1.2.0 | decisões de alvo exigem 1.2.0; o produto guarda cópia do arquivo anterior | TEST_AGENT |
| D-E2E-12 | Registro do debug comparado com a revisão do plano QUE A SESSÃO RECEBEU | as decisões tomadas na sessão fazem parte do conteúdo do plano (a revisão muda depois delas) | TEST_AGENT |
| D-E2E-13 | Após correção guiada que volta em "Requer revisão", avançar com "Próxima" | o produto não deixa Continuar; a decisão CONFIRMED já foi registrada pela correção | TEST_AGENT (limitação declarada) |
| D-E2E-14 | `CodegenPresenter` usa `showDialog()` | o diálogo abria 0×0 (encontrado pela homologação real) | TEST_AGENT |
| D-E2E-15 | Contrato `selectors-1.1.0` passa a descrever `label` e `expected.label` | o runtime aceitava e o esquema recusava | TEST_AGENT |
| D-E2E-16 | Gerador: imports só do que é usado, ordem do isort, quebra determinística em 120 colunas, `TypeError` | ruff (perfil do framework) no código gerado | TEST_AGENT |
| D-E2E-17 | Código final = regeneração (mesmo gerador, mesmo `.nekkora`, sem janela) sobre a pasta gerada pela GUI, com diff anexado | a correção D-E2E-16 é do gerador, não do modelo; a geração pela GUI já foi comprovada | TEST_AGENT |
| D-E2E-18 | Variante de laboratório gerada pelo mesmo gerador com configuração `lab`; réplica tornada fiel (link e placar como no site) | rodar o código gerado contra o oráculo sem adaptar o código | TEST_AGENT |
| D-E2E-19 | Tentativas reais rotuladas e todas relatadas (`depuracao-1`, `oficial`, `verificacao-codigo-final`) | navegador novo = tentativa nova; nada é escondido | TEST_AGENT |

## Desvios de ordem

- A missão pede o laboratório ANTES do site real no G6. Nesta execução o laboratório (réplica, conferida pelo oráculo)
  rodou DEPOIS das duas primeiras tentativas reais. O resultado do laboratório não mudou nenhuma decisão.
