# S4 — Regressão completa

Candidato: `f879af5` (branch `integracao/studio-consolidacao-001`), com o código idêntico a `fix/studio-integration-001` `b83f04d`. Linha de base: `da7bb94`, sem as correções.

Framework no checkout irmão: `main` (`ca34da8`, `nekkora_rpa` 0.3.0), a versão que os testes da AI Engine exigem. O checkout estava na branch local `fix/framework-f-fw-02-f-fw-01` e volta para ela no fim da missão. A 0.4.0 não foi adotada.

## 1. Windows

| | Comando | Commit | Ambiente | Testes | Falhas | Erros | Ignorados | Tempo |
|---|---|---|---|---|---|---|---|---|
| Linha de base | `./mvnw -o clean verify` | `da7bb94` | Windows 11 Pro 10.0.26200, OpenJDK 21.0.12 | 781 | 0 | 0 | 0 | 7m33s |
| **Candidato** | `./mvnw -o clean verify` | `f879af5` | idem | **788** | **0** | **0** | **0** | 8m22s |

Os 7 testes novos são os das correções (S3):
- `DevelopmentPanelUiTest.rodapeNaoCortaFechar`;
- os 3 de `CodeValidatedTest`;
- `DocstringEscapeTest`;
- `OptionalColumnsTest#contratoV5RecusaManifestoForaDoVocabulario`;
- `CodegenQualityRulesTest.textoDeDocstring`.

Os 16 módulos terminaram com SUCCESS: domínio, persistência, validação, semântica, gerador, Bridge, Recorder, Guided Debug, PDD visual, AI Engine, UI, app e design system. O empacotamento gera `nekkora-studio-app/target/nekkora-studio.jar` com SHA-256 `171fae59c8d8c265ab39eb4a67faba460431cd5ebeb5d247e4a86427912e40c9`; o Studio não é publicado. O resumo por módulo está em `evidencias/s4-windows-verify.txt`.

## 2. Linux: `tools/golden-linux.sh verify`

Contêiner `eclipse-temurin:21.0.12.1_1-jdk-resolute`, fixado por digest, com o `git archive` do commit candidato e da extensão `bbcf96d`.

| Testes | Falhas | Erros | Pulados | Tempo |
|---|---|---|---|---|
| **788** | **0** | **0** | **29** | 3m18s |

Os 29 pulados têm motivo de ambiente, conhecido e documentado nas missões anteriores. Não são aprovação:
- 28 testes precisam do interpretador Python com o framework, ausente na imagem de referência. Ficam em `CodegenRound3Test`, `FrameworkCapabilityCatalogTest`, `SupervisedRunTest`, `DependencyManagerTest`, `CodegenQualityFlowTest`, `ListFlowTest`, `OptionalColumnsTest`, `DocstringEscapeTest`, `SingleFlowTest`, `EnterNavigationNegativeTest` e `TabularReprTest`.
- 1 teste é do `AtomicSaveTest`: a permissão negada não vale para root.

Diferença em relação à linha de base anterior (27 pulados): mais 2, que são os dois testes novos com Python real, rodados no Windows (S3). Nenhuma falha herdada. O resumo está em `evidencias/s4-linux-verify.txt`.

## 3. Interface real nas escalas 100%, 150% e 200%

Roteiro: `evidencias/s4-escalas/Escalas.java`. Ele usa o Studio real (`StudioApplication.launch`) com o projeto `biografias.nekkora` gerado na homologação, em `-Dsun.java2d.uiScale` 1, 1,5 e 2, numa tela de 1920×1032 úteis. Faz o seguinte:
- captura a janela principal;
- abre cada menu e confere o mnemônico e se ele cabe na tela;
- abre a janela "Desenvolver automação" pelo `SwingDevelopmentHost` real, nos estados "código gerado", "em execução" e "com pergunta";
- confere que nenhum botão sai da janela;
- confere que o Tab percorre o rodapé até "Fechar".

O mesmo roteiro foi rodado na `main` `1cbf669`, compilada num worktree à parte, para separar regressão de defeito herdado.

| Escala | Candidato | `main` `1cbf669` |
|---|---|---|
| 100% | **14/14 OK** | 1 falha: "Executar teste autorizado" e **"Fechar" fora da janela** no estado "em execução" |
| 150% | **14/14 OK** | 1 falha: idem |
| 200% | 10/14, ver abaixo | 4 falhas: rodapé cortado nos três estados, além dos itens 1 e 2 abaixo |

As 4 falhas do candidato em 200% (tela lógica de 960×516):
1. **Janela principal 970×550:** a janela maximizada inclui as bordas invisíveis do Windows. É um falso positivo do roteiro e acontece igual na `main`.
2. **"Ver documentação" recortado:** o botão fica dentro da área com rolagem da aba Plano, só visível rolando. É igual na `main`.
3. **e 4. Aba Pendências apertada:** em 476 px de altura lógica, a lista de pendências fica com altura zero (as setas da barra de rolagem aparecem com altura negativa) e a primeira linha da pergunta fica parcialmente sob as abas.
   - Na `main`, a lista já cai para uma linha.
   - No candidato, as duas linhas do rodapé tomam essa altura.
   - Fica como **ressalva R-15**: só ocorre em 200% numa tela de 1080p. O rodapé, que antes perdia botões até em 100%, agora é correto em todas as escalas. Corrigir a aba Pendências exige redesenhá-la, o que fica para decisão da titular.

As capturas estão em `evidencias/s4-escalas/candidato-{1,1.5,2}/`. Também há capturas da `main` em 200% para comparação; as de 100% e 150% da `main` foram descartadas porque outra janela cobria a tela, e restaram só os resultados textuais. Os golden screenshots não foram regenerados: o `GoldenScreenshotTest` passou nos dois sistemas.

## 4. Geração de código

Coberta pelos testes acima, que incluem 40 do gerador e os fluxos de lista, execução única, tabular e colunas opcionais com Python real no Windows, e pela homologação pela interface (S5: 105/105).
