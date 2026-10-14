# STUDIO-001 — Plano e matriz de testes

Duas camadas de verificação:

1. **Automatizada (headless, `./mvnw verify`)** — domínio, componentes e a aplicação montada exatamente como em
   produção (`Studio.create`), com um usuário roteirizado que preenche os **mesmos** formulários dos diálogos reais
   (`ScriptedPrompts` + `StudioDialogs.trySubmit`).
2. **Janela real (`StudioEvidence`)** — aplicação aberta no Windows, teclado/mouse reais via `java.awt.Robot`,
   diálogos reais e capturas de tela. Execuções: 1366×768 (100%, quatro temas), 1920×1032 (100%) e 1920×1032 a 150%.

Como executar:

```bash
./mvnw verify
cd nekkora-studio-app && ../mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
java -cp "target/test-classes;target/classes;$(cat target/cp.txt)" com.nekkora.studio.app.evidence.StudioEvidence <saída> 1366 768 1366x768 --full
java -Dflatlaf.uiScale=1.5 -cp "..." com.nekkora.studio.app.evidence.StudioEvidence <saída> 1920 1032 escala150-1920x1032
```

## Matriz

| ID | Caso | Automatizado | Janela real |
|---|---|---|---|
| S001-001 | Inicialização sem autenticação/licença | `StudioFlowTest.inicializacao` | OK (3 execuções) |
| S001-002 | Estado vazio sem dados fictícios | `StudioFlowTest.estadoVazio` | OK + captura 01 |
| S001-003 | Criar automação (domínio real) | `AutomationModelTest.criarAutomacao`, `StudioFlowTest.criarAutomacao` | OK + capturas 02/03 |
| S001-004 | Validação de nome | `AutomationModelTest.nomeInvalido`/`outrosLimites`, `StudioFlowTest.validacao`, `StudioUiTest.formularioValidacao` | OK + captura 04 |
| S001-005 | Múltiplas automações, estado isolado | `AutomationModelTest.isolamentoEntreAutomacoes`, `StudioFlowTest.multiplasAutomacoes`, `fecharEReabrir` | — |
| S001-006 | Criar sessão / renomear | `AutomationModelTest.criarSessao`/`renomearSessao`, `StudioFlowTest.sessoes`/`renomearSessao` | — |
| S001-007 | Login, Consulta, Resultado em ordem | `AutomationModelTest.ordemDasSessoes`, `StudioFlowTest.sessoes` | OK + captura 05 |
| S001-008 | Adicionar etapa manual | `AutomationModelTest.adicionarEtapa`/`etapaInvalida`, `StudioFlowTest.adicionarEtapa` | OK + captura 06 |
| S001-009 | Sequência e numeração visual | `AutomationModelTest.sequencia`/`numeracaoNaoEIdentidade`, `StudioFlowTest.sequencia`, `StudioUiTest.visaoReconcilia` | OK + captura 07 |
| S001-010 | Isolamento entre sessões | `AutomationModelTest.isolamentoEntreSessoes`, `StudioFlowTest.isolamentoEntreSessoes` | — |
| S001-011 | Seleção atualiza propriedades | `StudioFlowTest.selecao`, `StudioUiTest.selecaoSemLacos` | OK (clique + seta) + captura 08 |
| S001-012 | Detalhes correspondem à etapa | `StudioFlowTest.detalhes` | OK + captura 09 |
| S001-013 | Sem identificadores internos | `StudioPrivacyTest`, `PrivacyAndRecorderReadinessTest` (toString, id/name HTML, sensível), `StudioUiTest.elementoSeguro` | OK (janela e diálogo sem UUID) |
| S001-014 | Tipos de ação | `AutomationModelTest.tiposDeAcao`, `StudioFlowTest.tiposDeAcao` | — |
| S001-015 | Sakura Dark | `StudioThemeAndKeyboardTest.sakuraDark` | captura 10-sakura |
| S001-016 | Alternância dos 4 temas sem perder estado | `StudioThemeAndKeyboardTest.alternanciaDeTemas`, `StudioUiTest.pintamNosQuatroTemas` | OK + 4 capturas |
| S001-017 | 100% e 150% | `Scale150Test` (JVM própria, 1366×768 lógicos = 2049×1152) | execução `escala150-1920x1032` |
| S001-018 | Teclado, foco, diálogos | `StudioThemeAndKeyboardTest.atalhos`/`tabNasAreasDeTexto`/`focoEAcessibilidade`/`semReentrada` | Ctrl+N, Ctrl+Shift+N, Ctrl+E, Tab, Shift+Tab, Enter, Esc, setas reais |
| S001-019 | Separação de camadas | `DomainLayerTest`, `StudioScopeTest.camadas`, `StudioUiTest.semDependenciaDaAplicacao` | — |
| S001-020 | Sem funcionalidades fictícias | `StudioScopeTest.futurasDesabilitadas`/`semFuncionalidadesFicticias` | toolbar com ações desabilitadas (capturas) |
| S001-021 | Aviso ao encerrar | `StudioScopeTest.encerramentoComAlteracoes`/`encerramentoSemAlteracoes` | OK + captura 11 |
| S001-022 | Regressão do Design System | 71 testes pré-existentes + `LayoutRegressionTest` (3 novos) | — |

Complementares: `PageMapContractTest` (compatibilidade com o PageMap 0.2.0 e "Selecionar São Paulo no campo Estado"
como uma etapa), `PrivacyAndRecorderReadinessTest.etapaGravadaPelaPorta` (CA-019: etapa `RECORDED` pela porta
`StepIntake`, sem UI), `DesignLintTest` em cada módulo de tela.

## Resultado (2026-10-14, Windows 11, JDK 21.0.12, tela 1920×1080)

| Módulo | Testes | Falhas | Ignorados |
|---|---|---|---|
| nekkora-design-tokens | 24 | 0 | 0 |
| nekkora-swing-ui | 45 (42 pré-existentes + 3 novos) | 0 | 0 |
| nekkora-swing-showcase | 5 | 0 | 0 |
| nekkora-studio-domain | 47 | 0 | 0 |
| nekkora-studio-ui | 12 | 0 | 0 |
| nekkora-studio-app | 28 + 1 (scale-150) | 0 | 0 |
| **Total** | **162** | **0** | **0** |

Janela real: 1366×768 — 20 verificações OK; 1920×1032 — 16 OK; 150% — 16 OK; 0 falhas. Registros:
`reports/studio-001/evidence/*-log.txt`.
