# NEKKORA-STUDIO-013 — resultados (Studio)

Execuções de 19/10/2026, com dados 100% sintéticos. Nenhum ambiente ou dado de cliente foi usado.

## Suítes

| Suíte | Resultado |
|---|---|
| Windows (`./mvnw test`, MS OpenJDK 21.0.12) | **640/640**. Base `main`: 624. Novos: `PddVisualV3Test` 7, `PddSitesUiTest` 6, `PddSitesClientTest` 3 |
| Linux (`tools/golden-linux.sh verify`, Temurin fixado por digest) | **639/639 + 1 ignorado** (640 no total; o ignorado é o mesmo das missões anteriores, só para Windows), Temurin 21.0.12.1 linux/amd64 |
| Contrato do Bridge | Schema e fixtures `s013-*` idênticos aos da extensão (13 válidas, 15 inválidas) |

## Homologação na janela real (`Studio013Homologation`)

A homologação usou o Studio de verdade com dados, Bridge e Studio isolados, o Chrome for Testing 155 com a extensão 0.6.0
desta branch, o Native Host do script oficial, o mouse e o teclado do sistema (Robot) e o gesto Alt+Shift+K real. A
página foi o "sistema real" sintético `studio013/sistema.html`, servido como `site-a.nekkora.test`. Operador: Claude como
agente de teste (TEST_AGENT).

**Resultado: 75/75 verificações; em 150%, 5/5.** Log completo: `evidence/S013-homologacao-log.txt`.

| Etapa | O que foi provado |
|---|---|
| A1 | Gravação no site autorizado pelo Chrome (preencher, preencher, clicar), sem valor digitado no modelo |
| A2 | "Esta página permite gravação, mas a captura de PDD ainda não está autorizada", com a origem exata e sem caminho. "Capturar" fica indisponível |
| A3 | "Autorizar captura PDD neste site" com clique real: só nesta sessão, registrado como agente de teste. Botões e lista inteiros dentro da janela |
| A4 | Captura v3 com gesto real. Rascunho 1.2.0. Escala 2. Só rótulos do vocabulário, inclusive de shadow roots aberta e fechada. Tabela e frame só como lugar. **Nenhum pixel da cor-sentinela**. Rascunho sem nenhuma das 20 sentinelas |
| A5 | Recaptura vira candidata; recorte pelo mouse; Aplicar |
| A6 | Marcadores automáticos 1/2/3 comprovados (o manual foi dispensado) e a automação intocada |
| A7 | O PDF declara "nenhum pixel da página foi copiado" e "Não é uma captura de tela real". Nenhuma sentinela no texto nem nas páginas renderizadas |
| A8 | Revogar pela lista deixa a captura indisponível. "Lembrar este site" registra o site como lembrado |
| A9 | Revogar com a captura armada cancela a captura |
| A10 | Acesso do Chrome revogado: a autorização PDD some. Devolver o acesso não a devolve |
| A11 | Laboratório local captura sem autorização (v3) |
| A12 | 4 temas. Rascunho, logs do host e observações do log sem sentinelas. Nenhuma evidência PNG com a cor-sentinela. `.nekkora` nunca mudou |

Evidências em `evidence/`:

- **Janela do PDD:** A2, A3, A4, A6 e temas.
- **Imagem sintética desenhada:** `S013-A4-imagem-sintetica-site.png`.
- **PDF:** `S013-pdd-visual-v001.pdf` e a página renderizada.
- **Texto extraído do PDF.**
- **150%:** em `evidence/150/`.

Todas as imagens são da janela do Studio ou desenhadas pelo Studio: nenhuma traz pixels da página.

## Teste de mutação

| id | Mutação | Resultado |
|---|---|---|
| S1 | 4ª barreira aceita rótulo fora do vocabulário | morta |
| S2 | Mistura fundo↔tinta sempre aceita | morta |
| S3 | Repintura exata fora dos rótulos desligada | morta |
| S4 | O desenho aceita descrição com rótulo fora do vocabulário | morta |
| S5 | Barras TEXT sem quantização | morta |
| S6 | v3 aceita portador de pixels | morta |
| S7 | Rascunho 1.1.0 aceita v3 | morta (sobreviveu na 1ª rodada; o caso que só essa regra pega foi acrescentado) |
| S8 | O Studio aceita origem com caminho | morta |
| S9 | Aba sem consentimento oferecida como pronta | morta |
| S10 | O Studio aceita v3 com imagem | morta |
| S11 | O Studio manda origem com caminho no consentimento | morta |
| S12 | v3 não reverificada antes de exportar | morta |

**12/12 mortas.**

## Defeito achado na homologação

Na janela do PDD, os rótulos longos da escolha de aba e da lista de sites empurravam "Capturar" e "Revogar captura PDD"
para fora da coluna; o clique real não alcançava o botão.

- **Correção:** as escolhas têm largura máxima e uma dica com o texto inteiro, e os botões ficam em linha própria.
- **Verificação:** a homologação passou a conferir que esses controles aparecem inteiros dentro da janela.
