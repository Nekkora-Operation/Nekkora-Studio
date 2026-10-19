# NEKKORA-STUDIO-013 — captura visual do PDD em sites autorizados (Studio)

Contrato, decisões D-S013-01..07 e política `nekkora-visual/3`: `Nekkora-Extension-Chrome/docs/studio-013/decisions.md`
(mesma branch `feat/nekkora-studio-013`). Este documento cobre o lado do Studio. Os resultados das execuções ficam em
`RESULTADOS.md`.

## Experiência

1. Abra a página no Chrome e conceda o acesso do Chrome à extensão, se ainda não tiver: ícone da extensão → Acesso ao
   site → Neste site. O Studio não concede acesso do Chrome.
2. No Studio, abra Automação → Gerar documentação → PDD visual… e escolha o bloco e a aba. A escolha de aba mostra o
   estado de cada uma:

   | Rótulo na lista | Estado | O que o Studio faz |
   |---|---|---|
   | `· laboratório` | READY | Captura sem pedir nada |
   | `· site autorizado para o PDD` | READY | Captura |
   | `· captura PDD não autorizada` | CONSENT_REQUIRED | Mostra *"Esta página permite gravação, mas a captura de PDD ainda não está autorizada. Site: <origem>"*, o botão **Autorizar captura PDD neste site** e a caixa **Lembrar este site** (sem marcar: só até fechar o navegador) |
   | `· sem acesso do Chrome` | CHROME_ACCESS_REQUIRED | Explica como conceder no Chrome. Não há botão: o Studio não concede |
   | `· em gravação` | RECORDING | Pede para finalizar a gravação |
   | `· captura indisponível` / `· benchmark (extensão antiga)` | UPDATE_EXTENSION | Avisa que a captura em site exige a extensão 0.6.0 |

   Quando nenhuma aba pode capturar, uma dica explica por quê, só com contagens: sem autorização PDD, sem acesso do
   Chrome, em gravação, bloqueadas pelo navegador. Nunca mostra endereço ou título.
3. Use **Capturar tela…** e depois o atalho **Alt+Shift+K** no Chrome. A imagem é **desenhada pelo Studio** a partir da
   descrição sintética. Nenhum pixel da página é copiado.
4. Revise como antes: candidata, Aplicar/Cancelar, **Selecionar área** e **Posicionar marcador**.
5. Exporte o PDF. O texto de procedência declara que a imagem é sintética e que não é uma captura de tela real.
6. Para revogar, use **Sites autorizados para o PDD** → **Revogar captura PDD**. Uma captura armada ou em curso nesse site é
   cancelada pela extensão.

## Peças

| Peça | Papel |
|---|---|
| `PddConsentClient` (recorder) | Comandos `studio.pdd.consent.status/grant/revoke/list`. Só aceita origem `esquema://host[:porta]`, escopo `SESSION`/`REMEMBERED` e ator `DEVELOPER`/`TEST_AGENT` |
| `BrowserTab` | Campos novos `origin` e `pddConsent`. Origem fora da forma é descartada; perfil desconhecido vira `null` (fail-closed) |
| `PddCaptureClient` | `supportsV3` (0.6.0+). `arm(..., policy, origin)`. Resultado v3 sem imagem; uma v3 com `png` é recusada |
| `SyntheticRenderer` (pdd) | Valida a descrição (mesma regra do host, com `CapturePrivacy.interfaceText`) e desenha em escala 2 (1 acima de 2048 px CSS), com Inter e paleta fechada; texto em tons de cinza |
| `VisualVerifier.verifyV3` | 4ª barreira, na recepção e antes de cada exportação: tipos, ordem, tetos, geometria em múltiplos da escala, barras quantizadas, rótulos do vocabulário e disjuntos, repintura exata fora dos rótulos, mistura fundo↔tinta dentro deles, sha-256 |
| `DocumentDraft`/`DraftJson` | Política `nekkora-visual/3`, regiões `TEXT`/`LABEL` e rascunho **1.2.0** só quando há v3. Versão e política precisam ser coerentes |
| `PdfRenderer` | Texto de procedência `PROVENANCE_V3`; recorte automático considera `LABEL` |
| `PddPresenter`/`PddPanel` | Estados das abas, autorização, revogação, lista de sites, dica de indisponibilidade, mensagens por motivo |

O ator do registro vem de `StudioEnvironment.decisionActor()`: `TEST_AGENT` só em modo de laboratório explícito. A lista
de sites mostra quem decidiu. Os consentimentos ficam na extensão e nunca no `.nekkora`. Revisar a imagem também não muda
a automação.

## Compatibilidade

- **Extensão 0.6.0:** o Studio pede a v3, inclusive no laboratório.
- **Extensão 0.5.x:** o Studio pede a v2, só no laboratório. Sites aparecem como indisponíveis e nenhum comando novo é
  enviado.
- **Extensões anteriores:** v1.
- **Rascunhos:** 1.0.0 e 1.1.0 continuam legíveis e exportáveis. Um Studio anterior recusa um rascunho 1.2.0 em vez de
  lê-lo errado.
