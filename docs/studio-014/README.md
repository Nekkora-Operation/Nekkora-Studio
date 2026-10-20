# STUDIO-014 — AI Development Engine e garantia de qualidade da automação

**Desenvolver automação** gera o projeto Python a partir do PDD e da gravação, num clique e sem questionário.
**Validar automação** mostra, por camada, o que foi verificado, o que foi corrigido, o que falta e quando é preciso ajuda.
Nada é executado no sistema-alvo, e a IA é opcional.

- Auditoria inicial (G0): [g0-auditoria.md](g0-auditoria.md)
- Decisões e contratos: [decisions.md](decisions.md)
- Segurança e privacidade: [security-and-privacy.md](security-and-privacy.md)
- O que não é garantido: [known-limitations.md](known-limitations.md)
- Relatório final: [../../reports/studio-014-final-report.md](../../reports/studio-014-final-report.md)
- Motor, Prompt-Mestre e catálogo: [../../Nekkora-AI-Engine/README.md](../../Nekkora-AI-Engine/README.md)

## Tutorial (desenvolvedora)

1. **Grave** o processo com o Recorder e confirme os alvos em **Depurar no navegador** (Ctrl+Shift+D).
2. Abra o **PDD visual** (Automação → Gerar documentação) e revise os textos marcados. Imagens são opcionais: sem elas,
   o documento declara a limitação visual.
3. **Salve o projeto** (Ctrl+S).
4. **Automação → Desenvolver automação** (Ctrl+Shift+G). A janela abre e a análise já começa:
   - *Analisando projeto → Verificando PDD → Organizando arquitetura e variáveis → Validando modelo → Gerando Python →
     Validando saída*.
   - Se faltar algo que o Studio não pode deduzir, a aba **Pendências** faz uma pergunta curta por etapa e diz por que e
     para onde vai a resposta. Exemplos: o efeito de um botão com rótulo desconhecido, a confirmação de um envio ou o
     endereço inicial. Responda pelo botão, e a análise recomeça sozinha.
   - A aba **Plano** responde: o que será criado; de onde vieram as etapas; quais funções serão usadas e **onde
     consultar** cada uma (arquivo e linha no pacote instalado, seção da documentação); como os dados chegam; para onde
     vai o Python; o que a IA não sabe.
   - A aba **Resultado** lista os arquivos (gravado, igual ou protegido), as verificações que **rodaram de verdade**
     (compilação, importação, ruff, mypy estrito e testes de contrato) e a linha "Execução real: não executada".
5. **Abrir código** abre `<projeto>-python/`. Para rodar no laboratório, siga o `README.md` gerado: `--entrada
   entrada.json` e os segredos em `NK_SECRET_*`.
6. **Automação → Validar automação** (Ctrl+Shift+V): a aba Validação mostra a linha de camadas
   `V0 Modelo · V1 PDD · V2 Capacidades · V3 Plano · V4 Código Python · V5 Testes · V6 Execução real` e, na mesma lista,
   os apontamentos estruturais e os das outras camadas. Use **Resolver…** para responder ou corrigir.

Nada disso exige abrir um `*.codegen.json`, escolher pasta ou cadastrar variáveis. **Opções avançadas** permite mudar a
pasta de saída, apontar o Python de verificação, ver a configuração derivada e usar o gerador antigo (configuração
manual).

## Arquitetura

```
.nekkora (modelo, StepId, decisões do debug)   PDD revisado + especificação funcional   catálogo do nekkora_rpa instalado
                \                                        |                                       /
                 ProjectContext (minimizado) ──► PddGate (PDD_READY) ──► DevelopmentPlanner ──► SingleGenerator / PythonGenerator
                                                                         (variáveis derivadas,      (gerador oficial: manifesto,
                                                                          Pages por tela, efeitos)    hashes, .novo/.diff)
                                                                                                         │
                                AutomationQualityGate (V0–V6) ◄── PythonChecks (ruff, mypy, contrato) ◄──┘
                                       ▲                                     IA opcional: AiAssist → PlanVerifier (só propostas)
```

Três autoridades, separadas:
- o **PDD revisado** e a **especificação funcional** dizem o que o processo faz;
- o **modelo** e o **debug guiado** dizem como as ações foram mapeadas;
- o **catálogo gerado** diz o que o framework realmente tem.

Estados do desenvolvimento: `PROJECT_ANALYZED → PDD_READY → PLAN_VALIDATED → CODE_GENERATED → CODE_VALIDATED`.

| Onde | O quê |
|---|---|
| `Nekkora-AI-Engine/` | motor (ver o README do módulo) |
| `nekkora-studio-codegen/.../SingleConfig.java`, `SingleGenerator.java`, `SingleEmitter.java` | gerador oficial para execução única (`codegen-config/1.1.0`) |
| `nekkora-studio-app/.../develop/DevelopmentPresenter.java`, `SwingDevelopmentHost.java` | ação, fases, respostas, verificação em segundo plano |
| `nekkora-studio-ui/.../develop/DevelopmentPanel.java` | janela (Plano, Pendências, Resultado, Opções avançadas) |
| `nekkora-studio-ui/.../ValidationPanel.java` | linha de camadas e apontamentos V1–V6 na aba Validação |
| `<dados>/desenvolvimento/<automationId>/` | especificação, último gate, configuração derivada, relatório de qualidade, opções |
