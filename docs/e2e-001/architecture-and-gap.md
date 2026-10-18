# NEKKORA-E2E-001 — G0: viabilidade, arquitetura e lacunas

Data: 2026-10-18. Operador: Claude (Claude Code) como `TEST_AGENT`. Status do G0: **PASS (diagnóstico)** — nenhum
bloqueio material de segurança, licença ou permissão geral; lacunas de implementação listadas abaixo.

## 1. Estado dos repositórios (fetch em 2026-10-18)

| Repositório | `main` | PRs anteriores | Branch da missão |
|---|---|---|---|
| Nekkora-Studio | `2a8211f` (merge do #13, STUDIO-010) | #9–#13 merged | `feat/e2e-001-rpa-challenge` |
| Nekkora-Extension-Chrome | `88df603` (merge do #9) | #5–#9 merged | `feat/e2e-001-rpa-challenge` |
| Nekkora-RPA-Framework | `cf518bf` (merge do #1, 0.1.0) | #1 merged | `feat/e2e-001-rpa-challenge` |

Ambiente: Windows 11 Pro 10.0.26200; Google Chrome 155.0.8059.80; Chrome for Testing da extensão
(`.tools/chrome-for-testing`); Native Host instalado pelo script oficial; Python 3.12.10; Playwright 1.63.0; Docker 29.

## 2. O site real (lido em 2026-10-18 com navegador isolado, sem clicar em Start — inspeção AUXILIAR do TEST_AGENT)

- `https://rpachallenge.com/` → 200, sem redirecionamento; `https://www.rpachallenge.com/` serve o mesmo conteúdo
  (não necessário). Origem canônica adotada: **`https://rpachallenge.com`** (somente).
- Instruções (texto real): transferir dados da planilha para os campos; os campos mudam de posição a cada submissão
  durante 10 rodadas; o cronômetro começa no Start; antes disso pode-se submeter sem penalidade.
- 7 campos `<input type=text>`: rótulos visíveis "First Name", "Last Name", "Company Name", "Role in Company",
  "Address", "Email", "Phone Number". **O `<label>` é irmão do campo, sem `for`**: o nome acessível ARIA do campo é
  vazio. `id`/`name` são aleatórios (5 caracteres) a cada carga. Atributo `ng-reflect-name` (`labelFirstName`…) existe
  (reflexo do Angular em modo de desenvolvimento — não é contrato do site).
- Botões: `START` (`<button>`), `Submit` (`<input type=submit>`); link "DOWNLOAD EXCEL" →
  `./assets/downloadFiles/challenge.xlsx` (mesma origem; `HEAD`: 200,
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, 34 124 bytes).
- Sem iframe e sem shadow DOM.
- Rede: além da própria origem, `fonts.googleapis.com`, `fonts.gstatic.com` (fontes) e
  `static.cloudflareinsights.com` (métrica). **Nenhum é necessário ao formulário** → ficam bloqueados nos navegadores
  da missão (não serão autorizados).
- `robots.txt`: só declaração genérica de "content signals", sem `Disallow` nem sinal aplicável. Uso previsto (desafio
  de treinamento) sem scraping, sem CAPTCHA observado.

## 3. Capacidades atuais × fluxo exigido

| Etapa | Capacidade atual | Lacuna |
|---|---|---|
| Autorização de origem (extensão) | categoria `AUTHORIZED` = permissão opcional do Chrome concedida ao site; concedida pelo controle do próprio Chrome ("Acesso ao site") | nenhuma: usar `https://rpachallenge.com/*` exato (autorização da usuária: texto da missão) |
| Recorder | grava em aba `LAB` ou `AUTHORIZED` | candidatos do campo seriam só `id`/`name` aleatórios: **não existe candidato por rótulo irmão** (LABEL só para `<label for>`/rótulo envolvente) |
| Privacidade | `nekkora-privacy/2`: rótulos e botões do desafio passam no vocabulário; "excel" não (texto do link de download é retido) | nenhuma mudança de política necessária para os campos |
| PDD visual | captura só em aba `LAB` (`VISUAL_CAPTURE_NOT_ALLOWED`) | **perfil explícito de benchmark** (origem exata, aditivo, sem reclassificar como LAB) na extensão e no Studio |
| Guided Debug | resolução somente leitura em aba autorizada; sonda (preencher/clicar) só LAB | nenhuma para verificar identidade; sonda continua LAB-only |
| Excel/CSV | framework 0.1.0 sem leitura tabular nem download | **FRAMEWORK-002**: XLSX (openpyxl, extra opcional), CSV, provider tabular, download validado |
| Origem externa no framework | só `lab` (loopback) | **perfil `benchmark`**: lista exata de origens https, mesmas guardas de rede |
| Identidade no framework | tag/tipo/papel/nome acessível | **rótulo visível** (rótulo irmão único) como evidência e estratégia, pois o nome ARIA é vazio |
| Geração Python | só o gerador de laboratório da STUDIO-009 (script avulso) | **STUDIO-011**: gerador integrado ao Studio (ação de menu), contrato exportável, configuração de dados tabulares, proteção contra edição manual |
| Loop por registro | engine do framework já é transacional (um item por linha) | o gerador precisa mapear sessão repetida por registro + inicialização (Start) |

## 4. Plano mínimo e genérico (GAP → solução → risco → PR → teste)

| # | Solução | Risco | PR | Teste |
|---|---|---|---|---|
| 1 | Framework 0.2.0: `nekkora_rpa.tabular` (XLSX/CSV, schema de colunas, limites, fórmulas recusadas, zip-bomb por cota), `TabularInputProvider` | dependência nova (openpyxl) — opcional, extra `excel` | Framework | unitários adversariais + XLSX real baixado |
| 2 | Framework: `Actions.download(key)` (origem, nome, tipo, tamanho, gravação atômica, SHA-256) | download fora da pasta controlada | Framework | laboratório (download válido, cancelado, tipo/tamanho errado, redirecionamento externo) |
| 3 | Framework: ambiente `benchmark` (origens https exatas, sem curinga) | ampliar acesso | Framework | LAB continua só loopback; origem fora da lista recusada; redirecionamento/terceiros bloqueados |
| 4 | Framework + extensão: identidade/estratégia por rótulo irmão único (`label`) | rótulo duplicado | Framework, Extensão | sósias (dois rótulos iguais) bloqueados |
| 5 | Extensão: perfil `benchmark-visual` para captura de PDD em origem exata autorizada | expor imagem externa | Extensão + Studio | `nekkora-visual/1` inalterada; captura fora do perfil recusada |
| 6 | Studio: STUDIO-011 gerador Python (configuração tabular, contrato, manifesto, hashes, recusa de sobrescrita, diff) | gerador "específico" | Studio | determinismo, edição manual detectada, ações não suportadas recusadas no pré-flight |

## 5. Operação da GUI

G2–G6 exigem o Studio real (Swing) operado por mouse/teclado do sistema (Robot) e o Chrome for Testing com a extensão
desta branch; os gestos na página (digitar, clicar, Alt+Shift+K) são do TEST_AGENT via CDP/Robot e ficam registrados
como tal. Isso exige máquina livre e "Não incomodar" (solicitação à usuária antes de rodar).
