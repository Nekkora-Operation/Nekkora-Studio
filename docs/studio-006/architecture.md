# STUDIO-006 — Arquitetura

A STUDIO-006 transforma o editor em memória num aplicativo que **salva, fecha, reabre e recupera** automações sem
perder identidade, procedência, revisões manuais nem evidências. Nada da Chrome Extension mudou: o salvamento
funciona com o navegador desconectado (D-S006-16).

## Módulos

```text
nekkora-studio-domain        (sem mudança de comportamento)
  └── AutomationRestoration  reconstrução controlada: dados validados → Automation (mesmos IDs, instantes, original)

nekkora-studio-persistence   NOVO — Java puro, sem Swing, sem rede; depende do domínio e só do Json do bridge
  ├── ProjectFormat          contrato: nomes de entrada, versões, limites do leitor
  ├── ProjectCodec           Automation ↔ documentos JSON próprios (escrita e leitura estrita)
  ├── Doc                    leitor estrito: campos exatos, tipos conferidos, onde falhou (sem conteúdo)
  ├── ProjectPackage         contêiner ZIP: escrita determinística, leitura controlada, manifesto + SHA-256
  ├── CapturedDataPolicy     nekkora-privacy/2 nos dados capturados (antes de confirmar e depois de ler)
  ├── ProjectRepository      gravação atômica verificada, conflito, cópia de segurança, temporários abandonados
  ├── ProjectFiles           ponta a ponta: fotografia → bytes → gravação → releitura; leitura → validação → reconstrução
  ├── ProjectRecovery        fotografias de recuperação (kind=RECOVERY), retenção, varredura
  └── ProjectException       códigos estáveis + parte do arquivo (nunca conteúdo)

nekkora-studio-app
  ├── project/               StudioDataDir (NEKKORA_STUDIO_DATA_DIR), RecentProjects, StudioSettings (consentimento)
  ├── StudioEnvironment      inMemory (testes) | at(dir) | installation()
  ├── workspace/ProjectState estado de persistência POR automação (geração, arquivo, erro, salvando, recuperado)
  ├── workspace/ProjectService salvar, salvar como, abrir, recarregar, fechar, sair, recuperação (EDT → worker → EDT)
  └── ui/                    menu Arquivo, títulos com *, estado na barra de status, diálogos (Design System)
```

## Mapeamento com a arquitetura sugerida (§6)

| Sugestão da missão | Implementação | Observação |
|---|---|---|
| `ProjectPersistenceService` | `ProjectService` (app) + `ProjectFiles` (persistência) | o serviço do app cuida de threads, estado e diálogos; `ProjectFiles` é a operação pura |
| `ProjectSnapshot` | `ProjectFiles.Snapshot` | documentos JSON imutáveis produzidos na EDT |
| `ProjectValidator` | `ProjectPackage.read` + `ProjectCodec.read` + `AutomationRestoration` + `CapturedDataPolicy` | quatro camadas, nesta ordem |
| `ProjectSerializer` | `ProjectCodec.documents` + `ProjectPackage.write` | escrita própria, nunca serialização automática |
| `ProjectRepository` | `ProjectRepository` | |
| `ProjectHydrator` / `AutomationHydrator` | `AutomationRestoration` (domínio) | dentro do pacote do domínio, para não abrir uma API pública de estado arbitrário |
| `ProjectDocument`, `AutomationDocument`… | documentos JSON do `ProjectCodec` (`Map`/`List` imutáveis na prática) + `AutomationRestoration.*Data` | ver [persistence-model.md](persistence-model.md) |

## Fluxos

Salvar (detalhes em [atomic-save.md](atomic-save.md)):

```text
EDT: Snapshot.of(automação) ──► worker: bytes → temporário → sync → releitura completa → conflito? → ATOMIC_MOVE
                                         └─► EDT: geração gravada = salva; aviso; recentes; recuperação superada apagada
```

Abrir (detalhes em [hydration.md](hydration.md)):

```text
worker: pacote (nomes, tamanhos, manifesto, SHA-256, JSON) → documentos (campos exatos) → reconstrução (invariantes do domínio)
        → privacidade v2 → conteúdo do arquivo não mudou durante a leitura
EDT:    só então: catálogo.adopt → histórico vazio → estado "Salvo" → aba
```

## Fronteiras respeitadas

- O domínio não conhece arquivo, ZIP nem JSON (D-S006-04). A única entrada nova é `AutomationRestoration.restore`,
  que reaplica todos os invariantes.
- `Automation.Snapshot` (desfazer/refazer) **não** é o formato (§15). Teste: `ProjectValidationTest` ("Automation.Snapshot
  não é o formato").
- A persistência não depende de Swing; o app não lê ZIP.
- Nenhuma rede (D-S006-17): o módulo não tem dependência de rede e o app só grava em caminhos locais.
