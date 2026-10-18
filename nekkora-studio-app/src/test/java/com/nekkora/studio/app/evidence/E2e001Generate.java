package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.codegen.CodegenPresenter;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectRepository;

import java.nio.file.Path;
import java.time.Clock;

/**
 * NEKKORA-E2E-001: gera com o MESMO gerador do menu "Automação → Gerar código Python…" ({@link CodegenPresenter#generate}),
 * a partir do {@code .nekkora} salvo na homologação, sem janela (a geração pela GUI é comprovada pela homologação real).
 * Usos: (a) variante para a réplica local do desafio — só a configuração de geração muda (ambiente {@code lab}, origem
 * loopback), o modelo é o mesmo; (b) regenerar sobre a pasta já gerada depois de uma correção do gerador, para que o
 * relatório de escrita (WRITTEN/UPDATED/UNCHANGED/PROTECTED) e o diff mostrem exatamente o que mudou.
 *
 * <p>Uso: {@code E2e001Generate <projeto.nekkora> <configuracao.codegen.json> <pasta-de-saida>}
 */
public final class E2e001Generate {

    private E2e001Generate() {
    }

    public static void main(String[] args) throws Exception {
        Automation a = new ProjectFiles(new ProjectRepository(), "e2e-001").load(Path.of(args[0]), Clock.systemUTC()).automation();
        System.out.print(CodegenPresenter.generate(a, Path.of(args[1]), Path.of(args[2])));
    }
}
