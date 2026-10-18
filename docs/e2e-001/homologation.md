# NEKKORA-E2E-001 — Homologação real

Data: 2026-10-18. Máquina: Windows 11 Pro 10.0.26200, 1920×1000 lógicos, "Não incomodar" ligado e máquina livre
(confirmado pela usuária). Operador: Claude como `TEST_AGENT` (`-Dnekkora.studio.decisionActor=TEST_AGENT`).

## 1. Montagem

- **Studio real** (`StudioApplication`) com dados ISOLADOS (`NEKKORA_BRIDGE_DIR`, `NEKKORA_DATA_DIR`,
  `NEKKORA_STUDIO_DATA_DIR` em pastas temporárias), janela 1180×1000 à esquerda.
- **Chrome for Testing 155** com a extensão DESTA branch (cópia de teste: só acrescenta a origem exata
  `https://rpachallenge.com/*` ao manifesto), janela 740×1000 à direita, perfil novo, resolução de nomes só para
  `rpachallenge.com` e loopback.
- **Native Host** instalado pelo script oficial (`install-host.ps1`), conexão autenticada Studio ⇄ host ⇄ extensão.
- Gestos: mouse e teclado do sistema (`java.awt.Robot`) no Studio e na página; texto digitado na página por
  `Input.insertText` (CDP, evento confiável, sem área de transferência).
- Harness: `nekkora-studio-app/src/test/java/com/nekkora/studio/app/evidence/E2e001Homologation.java`.

Comando (na raiz do Studio, com o classpath do app):

```
java -Dnekkora.studio.decisionActor=TEST_AGENT -Dnekkora.bridge.dir=<hb> -cp <classpath> \
  com.nekkora.studio.app.evidence.E2e001Homologation <saída> <repo-da-extensão> <transiente.json>
```

## 2. Resultado da rodada final: 56 verificações, 0 falhas

Log completo (sem valores): `evidence/g2-g5/homologacao-log.txt`.

| Gate | Verificações | Observado |
|---|---|---|
| G2 Recorder | 7 (+1 da conexão Studio ⇄ host ⇄ extensão) | aba do desafio AUTORIZADA; Preparação = clicar (download) + clicar (Start); Preenchimento = 7 × preencher + clicar (Submit); Conclusão = observar; proveniência RECORDER; 7 campos com candidato LABEL validado e preferido; nenhum valor no modelo; salvo e reaberto com os mesmos StepIds e variáveis |
| G3 PDD | 5 | textos revisados pelo TEST_AGENT; 2 capturas reais no perfil BENCHMARK; PDF de 2 páginas sem valores, 2 imagens sanitizadas, metadados controlados |
| G4 debug | 34 | 1ª passagem: 2 confirmadas direto, 8 por correção guiada; formulário recriado (Submit antes do Start); 2ª passagem: 10 de 10 campos em outra posição, 3 direto, 7 por correção; nunca Continuar sobre alvo incerto; toda escolha no navegador compatível com a ação; registro da mesma revisão de plano recebida pela sessão; todas as etapas CONFIRMED (TEST_AGENT) |
| G5 gerador | 9 | gerado pelo menu; revisão do plano no relatório = do debug; 2ª geração sem mudanças; edição à mão PROTEGIDA (`.novo` + `.diff`); geração limpa depois de desfazer; sem valores; sem posição; `.nekkora` e log sem valores |

Registro do debug (`G4-registro-debug.json`): 67 entradas (35 verificações, 17 decisões, 15 correções), 10
decisões finais CONFIRMED, 0 rejeitadas, 0 indecisas, 4 reaproveitadas, 0 sondas; exclusões declaradas: valores,
nomes/descrições, texto da página, falas do resolvedor, chaves de correlação, resultado de negócio.

## 3. Rodadas anteriores (defeitos encontrados pela homologação)

| Rodada | Falha | Causa | Correção |
|---|---|---|---|
| várias | host descartava etapas | descrição do localizador > 200 (contrato do Bridge) | extensão `1a77cad` |
| várias | exportação do PDD bloqueada | instruções com nome ARIA vazio exigem revisão | roteiro revisa os textos como TEST_AGENT |
| — | foco do Studio perdido ao salvar | diálogo de migração de formato 1.1.0 → 1.2.0 | roteiro aceita "Atualizar e salvar" (cópia de segurança do produto) |
| — | "registro da mesma revisão" falhava | comparação com a revisão DEPOIS das decisões | compara com a revisão recebida pela sessão |
| — | resultado da geração nunca aparecia | diálogo do gerador 0×0 | Studio `be485c1` |
| — | pré-voo `LOCATOR_POSITIONAL` no link de download | nome retido ("excel", "cloud" fora do vocabulário) | extensão `6307268` + Studio `744fc8e` (aprovado pela usuária) |
| — | pré-voo `LOCATOR_NOT_VALIDATED` | cópia do vocabulário do Studio desatualizada | Studio `744fc8e` |

## 4. Execuções do código gerado

| Rótulo | Código | Onde | Resultado (lido da página / do oráculo) |
|---|---|---|---|
| `depuracao-1` | gerado pela GUI (gerador anterior à correção de estilo) | site real, Chrome 155 | "Your success rate is 100% ( 70 out of 70 fields) in 2488 milliseconds"; 10 SUCCEEDED |
| `oficial` | idem, sem alteração | site real | "Your success rate is 100% ( 70 out of 70 fields) in 2404 milliseconds"; 10 SUCCEEDED |
| réplica | variante `lab` do mesmo gerador | réplica local + oráculo | 1 início, 10 submissões em ordem, todas corretas; placar 100% (70/70) |
| `verificacao-codigo-final` | regenerado após a correção do gerador | site real | ver relatório final |

Evidências: `evidence/g6/<rótulo>/` (eventos JSONL, checkpoint, resumo, observações, contagem dos resultados,
stdout/stderr) — varridas: nenhum valor da planilha.
