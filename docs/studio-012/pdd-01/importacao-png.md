# Importação de PNG local — estudo e BLOQUEIO

Situação: **bloqueada nesta entrega**. Não existe nenhuma rota de importação no código, nem escondida, nem desligada. A
solução entregue para "a captura automática não ficou boa" é a **captura manual assistida**: capturar de novo no Chrome,
revisar, recortar e aplicar, com a mesma política e as mesmas quatro barreiras.

## Por que não dá para garantir uma imagem importada

| Garantia que a captura no Chrome tem | Na imagem importada |
|---|---|
| Regiões vindas do DOM, no mesmo instante da imagem (ficha de layout antes e depois) | Não há DOM. O Studio não sabe onde está rótulo, campo, valor ou tabela. |
| Portador aprovado pela `nekkora-privacy/2` (texto inteiro do vocabulário, sem imagem de fundo, não coberto) | Não há texto para avaliar, só pixels. OCR seria uma inferência, não uma prova. |
| Verificação de pixels independente em 4 barreiras (fora dos portadores, só a paleta sintética, cor exata do lugar) | Não há o que comparar: o fundo É o conteúdo. |
| Gesto no Chrome, `activeTab`, origem autorizada, mesma aba do começo ao fim | A origem do arquivo é desconhecida (outro sistema, outra pessoa, montagem). |
| Imagem bruta nunca sai do service worker | O arquivo bruto já existe no disco, fora de qualquer controle do Studio. |

Máscaras feitas à mão não fecham a lacuna:

- **Máscara incompleta** (um dos testes de segurança pedidos): um único dígito de CPF fora da máscara já é vazamento,
  e não há como o Studio comprovar que a máscara cobre tudo, porque ele não sabe onde está o dado.
- **Texto pequeno, rotacionado, em imagem ou em cor parecida com o fundo** passa pela revisão humana com facilidade.
- **Metadados** (`tEXt`, `iTXt`, `zTXt`, `eXIf`, perfis ICC, chunks privados) podem carregar dados. A recodificação os
  remove, mas não resolve os pixels.

## O que seria preciso para liberar (proposta, não implementada)

1. Uma política própria versionada, `nekkora-import/1`, separada da `nekkora-visual/*`. Uma imagem importada nunca
   vira `nekkora-visual/1` ou `/2`, e o PDF diria "imagem importada e mascarada manualmente".
2. Aceitar só PNG estático (recusando APNG, SVG, GIF animado, PDF e URL), com tamanho e dimensões máximos, decodificado e
   **recodificado** sem nenhum chunk auxiliar.
3. Máscaras irreversíveis aplicadas no Studio (pixels substituídos, sem camada), recorte e prévia final; só o resultado
   processado vai ao armazenamento auxiliar, e o original é descartado da memória.
4. Uma validação independente: o mínimo honesto seria aprovar por **lista de permissão** (marcar o que pode aparecer e
   ocultar todo o resto), nunca por lista de bloqueio (marcar o que esconder).
5. Uma aprovação explícita da usuária para os riscos residuais (revisão humana como única barreira de conteúdo) e um
   registro por imagem (quem revisou, quando, hash).

Mesmo com tudo isso, a garantia seria "uma pessoa revisou", e não "o Studio comprovou". Por isso esta entrega não
implementa a importação e registra o bloqueio para decisão da usuária.

## Alternativa segura sugerida

Um "esboço" desenhado no próprio Studio, com blocos, campos e botões da paleta sintética sobre uma tela vazia. Ele não
importa nenhum pixel e pode ser marcado no PDF como "ilustração montada na revisão". Fica como sugestão para uma missão
futura, se a captura assistida não bastar.
