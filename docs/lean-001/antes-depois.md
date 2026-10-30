# STUDIO-LEAN-001: antes e depois (medido)

Mesma máquina, mesmo JDK 21.0.12, Framework irmão em `main`/0.3.0 nas duas baterias; medições de abertura com uma
CÓPIA do jar fora de `target/` e dados descartáveis (`NEKKORA_STUDIO_DATA_DIR`), 5 rodadas, 20 s ociosos.

| Medida | Antes (`91040a5`) | Depois (`feat/studio-lean-001`) | Diferença |
|---|---|---|---|
| Repositório rastreado | 54 765 002 bytes, 2 001 arquivos | +3 documentos `docs/lean-001` e 1 teste | nenhum arquivo removido |
| `nekkora-studio.jar` | 8 395 978 bytes, 2 761 entradas (SHA-256 `3f2f125a…`) | **8 395 124 bytes, 2 757 entradas** (SHA-256 `268cfb89…`) | −854 bytes (o `.ruff_cache` saiu) |
| Abertura (janela visível) | mediana **1 328 ms** (1 321–1 495) | mediana **1 340 ms** (1 325–1 575) | igual (ruído) |
| Conjunto de trabalho ocioso | mediana **152,5 MB** (147,4–153,2) | mediana **152,1 MB** (151,7–152,9) | igual |
| Memória privada ociosa | ~248 MB | ~246 MB | igual |
| Testes Windows (`clean verify`, com golden e escala 150%) | **780 / 0 falhas / 0 pulados** em 6 min 21 s | **781 / 0 / 0** em 5 min 58 s | +1 (guarda nova) |
| Pacote gerado de automação (Wikipédia) | 49 317 bytes, 11 arquivos | inalterado (o gerador não foi tocado) | — |

Experimentos medidos e NÃO adotados:

| Experimento | Resultado | Por que não |
|---|---|---|
| `minimizeJar` no shade | 7 692 808 bytes (−8,4%) | remove classes carregadas por nome (FlatLaf, commons-logging do PDFBox); seguro, ficaria em ~3% |
| `-Xms32m` no Studio | privada ~222 MB; conjunto de trabalho ~154 MB | ganho pequeno; só valeria para quem abre pelo `studio.cmd` |

## Conclusão

**`NO_SAFE_REDUCTION`** para peso de distribuição, abertura e memória: o jar é dominado por bibliotecas usadas de
verdade (PDFBox/FontBox na exportação e prévia do PDD, FlatLaf, fontes do design system). 82% do repositório é
evidência de auditoria, que não vai para o produto e não pode ser apagada.

O que mudou é verificável e não altera comportamento: o release deixa de levar cache local de ferramenta (o conteúdo
volta a ser o mesmo em qualquer máquina), uma guarda impede a regressão, e o AI Engine deixa de declarar uma dependência
que não usa.
