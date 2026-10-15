# Nekkora Studio — Nekkora Swing UI

Base visual do **Nekkora Studio** em Java Swing: um design system completo em que **nenhuma tela decide cor,
fonte, borda, espaçamento ou comportamento visual por conta própria**. As telas escolhem *intenções*
(`NekkoraButton.Variant.PRIMARY`, `Status.SUCCESS`, `TypeStyle.H2`, `Spacing.SPACE_6`) e a biblioteca decide
a aparência a partir dos tokens do tema ativo.

![Tela Studio fictícia — Sakura Dark](docs/img/studio-sakura-dark.png)

| Midnight Dark | Porcelain Light | Sage Light |
|---|---|---|
| ![](docs/img/studio-midnight-dark.png) | ![](docs/img/studio-porcelain-light.png) | ![](docs/img/studio-sage-light.png) |

## Módulos

```
nekkora-design-tokens   → tokens semânticos + 4 temas (sem Swing)
nekkora-swing-ui        → tema/LaF, tipografia, ícones, marca, componentes e padrões genéricos de estúdio
nekkora-swing-showcase  → "Nekkora Design System Showcase" + tela Studio fictícia (demonstração)
nekkora-studio-domain   → modelo de automações: Automação → Sessões lógicas → Etapas (Java puro)
nekkora-studio-bridge   → Studio Bridge nekkora-bridge/1: canal local autenticado com o Native Host (Java puro)
nekkora-studio-recorder → RecorderController: gravação técnica confirmada pela extensão (Java puro)
nekkora-studio-ui       → peças visuais do Studio (navegação, automação, sessões, etapas, propriedades, diálogos,
                          Floating Recorder)
nekkora-studio-app      → a aplicação Nekkora Studio (jar executável)
```

O Nekkora Studio funcional nasceu na missão STUDIO-001 — veja [docs/studio-001](docs/studio-001/architecture.md) e o
[relatório](reports/studio-001-final-report.md).

**STUDIO-002 — gravação técnica controlada pelo Studio.** Com o Chrome aberto e "Conectar ao Nekkora Studio" ligado no
painel da [Nekkora Extension](https://github.com/Nekkora-Operation/Nekkora-Extension-Chrome), o botão **Gravar** inicia
uma captura técnica real numa aba autorizada e abre o **Floating Recorder** (pausar, continuar, modo, nova sessão,
finalizar, cancelar). Nesta versão a gravação acompanha a página, mas ainda **não cria etapas** (STUDIO-003) e
finalizar **não exporta** PageMap. Veja [docs/studio-002](docs/studio-002/architecture.md) e o
[relatório](reports/studio-002-final-report.md).

Camadas: **Tokens → Temas → Primitivas → Componentes → Padrões → Studio**. Detalhes em
[docs/ARQUITETURA.md](docs/ARQUITETURA.md).

## Requisitos

- JDK 21+
- Maven 3.9+ (ou o wrapper incluído: `./mvnw` / `mvnw.cmd`)

## Comandos

```bash
./mvnw verify                      # compila e roda a suíte (tokens, design system, Showcase, Studio, Bridge, Recorder)
tools/golden-linux.sh verify       # a mesma suíte no ambiente Linux de referência (Docker, JDK fixado por digest)
./studio.sh                        # abre o Nekkora Studio (Windows: studio.cmd)
./studio.sh --theme=porcelain-light
java -jar nekkora-studio-app/target/nekkora-studio.jar          # depois de ./mvnw package
./showcase.sh                      # abre o Showcase (Windows: showcase.cmd)
./showcase.sh --studio             # abre direto a tela Studio fictícia
./showcase.sh --theme=midnight-dark --page=dados
java -Dflatlaf.uiScale=1.5 -jar nekkora-swing-showcase/target/nekkora-showcase.jar   # simular 150% de DPI
```

Atalhos no Showcase: **Ctrl+Shift+T** alterna tema. Na tela Studio: **F5** executar, **Ctrl+S** salvar,
**Ctrl+B / Ctrl+Alt+B** recolhem navegador/propriedades, **Ctrl+K** busca, **Shift+F10** menu de contexto.

## Uso rápido

```java
public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
        Nekkora.install();                              // tema salvo (ou Sakura Dark), fontes, tooltips
        NekkoraFrame frame = new NekkoraFrame("Nekkora Studio");

        NekkoraFormField nome = new NekkoraFormField("Nome", new NekkoraTextField("Ex.: Conciliação"))
                .required().helper("Aparece na navegação.");
        NekkoraButton salvar = NekkoraButton.primary("Salvar").withIcon(IconName.SAVE);

        NekkoraStack form = NekkoraStack.vertical(Spacing.SPACE_6);
        form.setPadding(Spacing.SPACE_8);
        form.addAll(nome, new NekkoraStatusBadge(Status.RUNNING), salvar);

        frame.getContentPane().add(form);
        frame.sizeToScreen();
        frame.setVisible(true);
    });
}

ThemeManager.setTheme(NekkoraThemes.PORCELAIN_LIGHT);   // ao vivo, sem reiniciar
```

## Temas

| Tema | Modo | Caráter |
|---|---|---|
| **Sakura Dark** (padrão) | escuro | identidade/autoral — rosa elegante sobre ameixa |
| **Midnight Dark** | escuro | técnico/profissional |
| **Porcelain Light** | claro | neutro/institucional |
| **Sage Light** | claro | calmo e sofisticado |

As paletas são as mesmas da `nekkora-lib` (web). Os tokens são derivados da paleta por regras de modo e
**testados para contraste WCAG AA** (texto, ações, status, foco).

## Documentação

- [Arquitetura](docs/ARQUITETURA.md) — camadas, tokens, tema, FlatLaf como infraestrutura, DPI, acessibilidade.
- [Componentes](docs/COMPONENTES.md) — catálogo da V1 e como usar.
- [Contribuindo](docs/CONTRIBUINDO.md) — regra de contribuição, lint de design, testes, Definition of Done.

## Licenças de fontes

Sora, Inter e JetBrains Mono são distribuídas com a biblioteca sob a SIL Open Font License 1.1
(`nekkora-swing-ui/src/main/resources/com/nekkora/ui/fonts/OFL-*.txt`).
