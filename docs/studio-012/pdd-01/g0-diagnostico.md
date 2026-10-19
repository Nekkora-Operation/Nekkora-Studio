# STUDIO-012 · Adendo PDD-01 — G0: diagnóstico e desenho

Data: 18/10/2026. Base: `main` do Studio (7a064b3) e da extensão (21c8600), com a STUDIO-012 já integrada.

## 1. Sintoma (vistoria manual)

No PDF exportado durante a vistoria (projeto de reprodução, sistema de homologação indicado pela usuária; o arquivo não
entra no repositório), a seção "1. Login" mostra:

- fundo cinza uniforme ocupando a imagem toda;
- os rótulos "Usuário:" e "Senha:" como pequenos recortes com o fundo real da página;
- dois campos brancos com moldura e os marcadores 1 e 2;
- um retângulo **vazio** com o marcador 3 no lugar do botão "Entrar".

Texto e marcadores estão corretos; a imagem não comunica a estrutura da tela.

## 2. Causas comprovadas

| # | Causa | Onde | Prova |
|---|-------|------|-------|
| C1 | `nekkora-visual/1` começa a imagem **inteira** opaca `#D5D8DD` e só devolve pixels reais nos portadores. Tudo o que não é rótulo nem campo vira cinza: cabeçalho, cartão do formulário, logotipo, rodapé. | `visual-sanitizer.js` `compose()`; `PddVisualPrivacy`; `VisualVerifier` | O cinza é o **mecanismo de ocultação**, não um defeito do PDFBox: a verificação de pixels aceita só `#D5D8DD`, `#FFFFFF` e `#8A9099` fora dos portadores. |
| C2 | O botão é `<input type="submit" value="Entrar">`. O plano visual considera portador só `button`, `summary`, `label`, `legend`, `a[href]` e `[role]`; e exclui `input[type=submit]` da lista de campos. Ele não é nem portador nem campo, e fica cinza. O marcador 3 aparece porque o alvo foi resolvido por identidade, mas não há nada desenhado dentro dele. | `content-main.js` `cs.pdd.plan` | A mesma estrutura na página sintética `studio012/pdd-login.html` reproduz o retângulo vazio (seção 6). |
| C3 | Os rótulos aparecem como "remendos" com o fundo real (cinza-claro do cartão) sobre o cinza sintético. | consequência de C1 | Mesmo PDF. |

## 3. Achados laterais (vistoria do código)

| # | Achado | Efeito | Tratamento |
|---|--------|--------|------------|
| A1 | Remover uma imagem apaga o PNG do armazenamento na hora (`retain`). Ao desfazer, o rascunho volta a apontar para um arquivo que não existe mais. | Desfazer seguido de exportar → `CAPTURE_FILE_MISSING`; a prévia mostra "Imagem indisponível". | A retenção passa a considerar o histórico de desfazer/refazer da sessão. Quando o histórico acaba (troca de automação ou Studio encerrando) e quando um rascunho legível é aberto com histórico novo, só ficam no disco as imagens que o rascunho referencia. Fechar e reabrir a janela do PDD não encerra a sessão: o Desfazer continua valendo. |
| A2 | O mapa de capturas do rascunho usa o sha-256 do PNG como chave. Dois blocos com a **mesma** imagem (a mesma tela, sem mudança) colidem: o segundo sobrescreve os metadados do primeiro, que fica com `CAPTURE_MISMATCH`. | Bloqueia a exportação sem motivo real. | No rascunho 1.1.0, cada captura ganha uma referência própria (`ref`), independente do conteúdo do PNG. |
| A3 | O jar empacotado do Studio não contém `nekkora-studio-semantics`: o `pom.xml` do app declara o módulo com escopo `test`, e essa declaração vence a dependência transitiva de `nekkora-studio-guided`. | O debug guiado quebra no jar (`NoClassDefFoundError: SemanticContract`); os testes não percebem porque têm o módulo no classpath de teste. | Escopo corrigido e teste que confere o conteúdo do jar sombreado. |

## 4. Desenho: `nekkora-visual/2` (representação documental)

Princípio: nada do fundo real é copiado. A imagem é **redesenhada** a partir de metadados geométricos do DOM, com
paleta fechada; os únicos pixels reais continuam sendo os dos portadores de rótulos aprovados pela `nekkora-privacy/2`.

### 4.1 Tipos de região

| Tipo | Origem no DOM | Desenho | Conteúdo real? |
|------|---------------|---------|----------------|
| `PAGE` (implícito) | viewport | `#FFFFFF` | não |
| `SURFACE` (`tone` LIGHT/MID/DARK) | contêiner visível com fundo próprio ou borda (≥ 96×32 px CSS); tabela inteira = 1 superfície, sem descer nas células | preenchimento do tom + borda 1 px | não (tom = classe de luminância do fundo, não a cor) |
| `MEDIA` | `img`, `svg`, `canvas`, `video`, `picture` (≥ 16×16 px CSS) | marcador de lugar | não |
| `FRAME` | `iframe`/`frame`/`object`/`embed` | marcador de lugar + limitação `FRAMES_HIDDEN` | não (o conteúdo do frame nunca é lido) |
| `CONTROL` | botão cujo rótulo não é elegível | formato de botão vazio | não |
| `FIELD` | campo editável | moldura branca (igual à v1) | não |
| `CARRIER` | rótulo elegível (v1) **+** `input[type=submit|button|reset]` com `value` aprovado pelo vocabulário | pixels reais | **sim**, só o rótulo |

### 4.2 Verificação (as quatro barreiras: service worker, Native Host, Studio na entrada e Studio antes de exportar)

A v1 aceitava qualquer uma das três cores em qualquer lugar fora dos portadores. A v2 é **mais estrita**: o verificador
repinta a "tela esperada" na ordem declarada (página, superfícies, mídia, frames, controles, campos) e exige que **cada**
pixel fora dos portadores seja exatamente a cor esperada naquele ponto. Um pixel real fora de um portador, uma região
declarada sem desenho correspondente ou uma cor fora da paleta reprovam a imagem.

Tetos novos: superfícies ≤ 48, mídia ≤ 16, frames ≤ 16, controles ≤ 32, portadores ≤ 64, campos ≤ 64. Os tetos de
altura e de área dos portadores são os mesmos da v1.

### 4.3 Compatibilidade

- `nekkora-visual/1` continua existindo sem mudanças: o Studio novo lê, mostra e exporta rascunhos com capturas v1.
- O Studio pede a v2 explicitamente ao armar (`policy`), e só para uma extensão que a declare. Com uma extensão antiga,
  continua pedindo a v1.
- Rascunho: só sobe para `pdd-visual/1.1.0` quando uma novidade é usada (captura v2, recorte, marcador manual ou duas
  capturas com o mesmo PNG). Sem novidade, ele continua sendo gravado byte a byte em 1.0.0. A subida é avisada na tela.

## 5. Captura manual assistida, recorte e marcadores

- **Capturar novamente** arma uma captura nova. A imagem que chega vira **candidata** (só em memória), e a anterior
  continua associada ao bloco. A prévia mostra o PDF com a candidata. **Aplicar** grava a candidata; **Cancelar**, uma
  falha, a troca de aba ou uma mudança no processo a descartam, e a anterior fica.
- **Selecionar área / Ajustar recorte**: retângulo em px do dispositivo sobre a imagem já sanitizada (limites, tamanho
  mínimo, inteiros). O recorte é **metadado**: o PNG verificado não muda, e o recorte só reduz a área mostrada.
- **Marcador manual**: só para instrução **sem** marcador automático. Fica gravado como `MANUAL` + `DEVELOPER`/`TEST_AGENT`,
  aparece no PDF com contorno tracejado e a nota "marcador posicionado manualmente". Não comprova a identidade do
  elemento e não muda a etapa, os localizadores nem o `.nekkora`. Uma captura nova ou um bloco alterado descartam os
  marcadores manuais.

## 6. Importação de PNG local

Estudada em `importacao-png.md`. Conclusão: **bloqueada nesta entrega**. Uma imagem arbitrária não tem regiões
comprovadas pelo DOM, e as máscaras feitas à mão não provam a ausência de dado sensível. Os detalhes estão no documento.
