# NEKKORA-STUDIO-002 — Relatório final (Nekkora Studio)

**Recorder Controller, Floating Recorder e Studio–Chrome Bridge.** Branch `feat/nekkora-studio-002`. O lado da extensão
está no PR do repositório Nekkora-Extension-Chrome (`feat/nekkora-studio-002-bridge`), com o relatório
`reports/studio-002-bridge-final-report.md`.

## 1. Baseline inicial

| Item | Valor |
|---|---|
| Studio | `26fa751` (main; merge do PR #2), igual ao baseline da missão |
| Extensão | `3c25a77` (main), igual ao baseline da missão |
| `./mvnw clean verify` (Windows, Microsoft OpenJDK 21.0.12) | **162** testes, 0 falhas |
| `tools/golden-linux.sh verify` (Temurin 21.0.12.1+1 fixado por digest) | **162** testes, 0 falhas, 0 ignorados |
| Extensão (`run-tests.ps1 -Suite all`) | Java 208 · Node 26 · navegador 186, 0 falhas |

## 2. Commits

Ver a lista no PR: Bridge; Recorder; integração na interface; homologação; documentação e relatório.

## 3. Componentes implementados

| Módulo | Componentes |
|---|---|
| `nekkora-studio-bridge` (novo, Java puro) | `BridgeServer` (StudioBridgeServer), `BridgeDirectory`, `BridgeAuth`, `BridgeFrames`, `Json`, `BrowserLink`, `LinkStatus`, `BridgeProtocol`, códigos `BridgeError` |
| `nekkora-studio-recorder` (novo, Java puro) | `RecorderController`, `RecorderState`, `RecorderTransitions`, `RecorderSnapshot`, `TechnicalRecording`, `RecordingMode`, `BrowserTab`/`TabAccess`, `RecordingProgress`, `RecordingSummary` |
| `nekkora-studio-ui` | `FloatingRecorderPanel`, `FloatingRecorderWindow`, `RecordingSetupPanel`, `RecorderText`; atualização incremental de `AutomationOverview`/`LogicalSessionView` (seção 20) |
| `nekkora-studio-app` | `RecorderPresenter`, `RecorderPrompts`/`SwingRecorderPrompts`, `RecorderWindowHost`/`SwingRecorderWindowHost`; Gravar real; item de status "Navegador"; saída do Studio protegida pelo Recorder; Bridge publicado em `StudioApplication.main` |
| Domínio | **intocado** (`StepIntake` disponível para a STUDIO-003) |

## 4. Arquitetura final e 5. diagrama

`docs/studio-002/architecture.md`:

```
Studio (RecorderPresenter ─ RecorderController ─ BridgeServer) ══UDS/HMAC══ Native Host ══Native Messaging══ extensão ─ CaptureSession
```

## 6. Contratos

`nekkora-bridge/1`: o schema e as fixtures ficam na extensão. Aqui há uma cópia em
`nekkora-studio-bridge/src/test/resources/contracts/`, e o `BridgeContractFixturesTest` acusa divergência e confere o
vocabulário (comandos, eventos, estados, modos e códigos). O PageMap 0.2.0 não mudou.

## 7. Máquina de estados

`docs/studio-002/recorder-controller.md`: `IDLE → STARTING → RECORDING ⇄ PAUSING/PAUSED/RESUMING`,
`FINALIZING → COMPLETED`, `CANCELLING → CANCELLED`, `DEGRADED ⇄ RECOVERING`, `ERROR`. Nenhum estado confirmado é
alcançado sem passar pelo pendente, e o teste verifica isso.

## 8. Segurança

`docs/studio-002/security.md`. Canal só local, ACL efetiva conferida, credencial efêmera e mútua, instância única e um
controlador por vez. Limite declarado: processo malicioso do mesmo usuário.

## 9. Recuperação

`docs/studio-002/lifecycle.md`. Concessão de 30 s renovada a cada 10 s; saída normal finaliza ou cancela antes de
fechar; queda e Chrome fechado são tratados; o reinício do service worker é reconciliado.

## 10. Testes realizados e 12. resultados

### Studio

| Módulo | Antes | Depois | Falhas |
|---|---|---|---|
| design-tokens | 24 | 24 | 0 |
| swing-ui (inclui 4 golden) | 45 | 45 | 0 |
| showcase | 5 | 5 | 0 |
| studio-domain | 47 | 47 | 0 |
| **studio-bridge** (novo) | — | 14 | 0 |
| **studio-recorder** (novo) | — | 21 (inclui 2 cruzados com o Native Host REAL) | 0 |
| studio-ui | 12 | 14 | 0 |
| studio-app (inclui `Scale150Test` em JVM própria) | 29 | 39 | 0 |
| **Total Windows** (`./mvnw clean verify`) | **162** | **209** | **0** |
| **Total Linux** (`tools/golden-linux.sh verify`) | 162 | **209** (0 ignorados) | **0** |

#### 12.1 Linux

`tools/golden-linux.sh verify` (Temurin 21.0.12.1+1, imagem fixada por digest), com a extensão do PR ao lado: o
script compila o Native Host dela dentro do contêiner. Resultado: **209 testes, 0 falhas, 0 ignorados**. Isso inclui
os testes cruzados com o Native Host real (`RealNativeHostBridgeTest`) pelo Unix Domain Socket no Linux e os 4 golden
Linux sem regravação.

#### 12.2 Homologação real (Windows 11)

`Studio002Homologation`: **42 verificações, 0 falhas** (log em `reports/studio-002/evidence/S002-homologacao-log.txt`).
A concessão de 10 s expirou **10.143 ms** depois que o processo do Studio foi derrubado. O consumidor do Recorder foi
liberado e a inspeção manual continuou ativa.

### Matriz S002

`docs/studio-002/test-plan.md`: **S002-001 a S002-030, todos ✅**, com o nível de cada evidência (unidade, contrato,
cruzado, navegador e homologação).

### Testes da STUDIO-001 com premissa alterada por esta missão

| Teste | Antes | Agora | Força |
|---|---|---|---|
| `StudioScopeTest.futurasDesabilitadas` | Gravar, Executar e Salvar desabilitados (3 botões) | Executar e Salvar desabilitados (2); Gravar é real, desabilitado sem destino e com o rótulo "Gravar" | mantida; o comportamento real do Gravar é verificado em `RecorderStudioTest` |
| `StudioScopeTest.semFuncionalidadesFicticias` | classes com "recorder" proibidas; "gravando" proibido | "recorder" permitido (escopo da missão); proibidos acrescentados: "sensor", "clickcapture", "Ai…", "etapas gravadas", "cliques capturados"; varredura estendida a bridge e recorder; "Gravando" permitido **só** na linha que devolve o rótulo do estado RECORDING confirmado | ampliada |
| `StudioScopeTest.camadas` | domínio, UI e app | + bridge sem domínio e sem Swing; recorder sem Swing; design system sem depender dos módulos novos | ampliada |

Nenhum teste foi removido; nenhum critério ou tolerância foi relaxado; os golden screenshots não foram regenerados.

## 11. Evidências

`reports/studio-002/evidence/`, da homologação real no Windows 11 (Studio de verdade, Chrome for Testing 155, Native
Host instalado pelo `install-host.ps1`, teclado e mouse reais). O log completo está em `S002-homologacao-log.txt`.

| Captura | O que mostra |
|---|---|
| `S002-01-studio-sem-chrome.png` | Studio aberto sem Chrome: "Navegador: aguardando conexão com a extensão" |
| `S002-02-conectado.png`, `S002-02b-painel-extensao-conectado.png` | conexão autenticada; painel da extensão "Conectada ao Nekkora Studio" |
| `S002-03-dialogo-iniciar-gravacao.png` | diálogo de início: sessão, abas (título mascarado e host), modo, avisos |
| `S002-04-floating-gravando.png` | Floating Recorder sobre o Chrome; botão "Gravando" após a confirmação |
| `S002-05-pausado.png` | pausa confirmada (consumidor liberado) |
| `S002-06-modo-e-sessao.png` | modo confirmado e troca de sessão sem nova captura |
| `S002-07-tema-*.png` | Floating Recorder nos 4 temas |
| `S002-08-resumo-finalizado.png` | resumo: sem etapas, sem PageMap |
| `S002-09-coexistencia-pausado.png` | Recorder pausado com a inspeção manual ativa |
| `S002-10-site-nao-autorizado.png` | site sem autorização desabilitado, com orientação |
| `S002-11-permissao-revogada.png` | revogação → "Captura interrompida" |
| `S002-12-chrome-encerrado.png` | Chrome fechado → "Conexão com o navegador perdida", "Encerrar no Studio" |
| `S002-13-sair-com-gravacao.png` | saída do Studio com gravação → Finalizar e sair |
| `S002-14-studio-derrubado-concessao-expirada.png` | Studio (processo separado) derrubado: concessão expirada, inspeção preservada |

## 13. Falhas encontradas e 14. correções

| Falha (encontrada por teste ou homologação real) | Correção |
|---|---|
| A janela flutuante **recebia o foco** ao aparecer (homologação) | aparece não focável e volta a aceitar foco 400 ms depois; homologação "não roubou o foco" ✅ |
| Avisos da versão truncados com "…" (captura de tela) | parágrafos com quebra de linha |
| Lista de abas com "Página interna do navegador — Página interna do navegador" | rótulo sem duplicação; autorizadas primeiro, internas por último |
| Selo de estado com descrição acessível "status: Em execução" | descrição própria: "Estado confirmado pela extensão" ou "Aguardando a confirmação…" |
| No encerramento do Studio, o resultado confirmado não era publicado | o encerramento passa por FINALIZING/CANCELLING e publica a confirmação (teste) |
| Na suíte completa da extensão, a gravação não sobrevivia ao reinício do service worker | vazamento antigo do `storage.session` na extensão (cota esgotada), corrigido lá |
| No Linux, publicar o Bridge falhava com `ENXIO` (encontrado pelo `golden-linux.sh verify`): restringir o socket com `NOFOLLOW_LINKS` faz o Java **abrir** o arquivo, e um Unix Domain Socket não se abre | `chmod` pelo caminho, dentro de um diretório 700 já conferido; teste novo de permissões POSIX para grupo e outros |
| No Linux, os testes cruzados com o Native Host real eram ignorados porque o host da extensão não estava compilado no contêiner | `golden-linux.sh verify` compila o Native Host da extensão ao lado; agora são 0 ignorados |
| `developerPrivate.addHostPermission` não concede permissões opcionais (homologação) | a cópia de teste da extensão declara `site-a.nekkora.test`; concessão e revogação feitas pelo controle "Acesso ao site" do Chrome, sem contornar o diálogo de permissões |
| A homologação travava no fim: `HttpClient.close()` esperava WebSockets do DevTools ainda abertos | `shutdownNow()` e fechamento explícito das páginas do roteiro |

## 15. Limitações e 16. pendências

`docs/studio-002/known-limitations.md`. Nenhuma pendência bloqueante. Em resumo:

- a captura de ações e os sensores dos modos ficam para a STUDIO-003;
- o opt-in no painel é obrigatório;
- há um navegador e uma instância por vez;
- retomar espera o motor se houver uma exportação em curso;
- a homologação com janela foi feita só no Windows.

## 17. Preparação para a STUDIO-003

- `TechnicalRecording` já guarda o histórico de associações com sessões lógicas: as etapas capturadas podem ser
  direcionadas à sessão vigente em cada instante.
- A porta `StepIntake` (`WorkspaceService.appendStep`) continua confinada à EDT e intocada. O `RecorderPresenter` já
  recebe eventos do Recorder pela EDT, que é o caminho natural para entregar etapas.
- A atualização incremental (seção 20) já relê só a sessão afetada e renumera as seguintes. Gravações contínuas não
  reconstroem a tela inteira.
- O modo confirmado já chega à extensão; o contrato aceita eventos novos por acréscimo.

---

## Resultados finais

| Verificação | Resultado |
|---|---|
| Windows `./mvnw clean verify` | **209** testes, 0 falhas, 0 ignorados (baseline 162) |
| Linux `tools/golden-linux.sh verify` | **209** testes, 0 falhas, 0 ignorados |
| Homologação real no Windows | **42** verificações, 0 falhas; 18 capturas |
| Golden screenshots | inalterados (não regenerados) |
| Matriz S002-001..030 | todos ✅ (`docs/studio-002/test-plan.md`) |
| Merge | **não realizado**: o PR aguarda revisão |

As capturas de tela inteira foram recortadas para mostrar só as janelas do Studio e do Chrome de teste. O conteúdo
das telas não foi alterado.
