package com.nekkora.studio.domain;

/**
 * Contexto técnico opcional de uma etapa: onde ela acontece. Não presume navegador — {@code application}
 * comporta sistemas desktop, arquivos ou APIs em missões futuras. Os textos chegam sanitizados pelo
 * produtor e passam por {@link com.nekkora.studio.domain.privacy.DisplaySafety} antes de serem exibidos.
 *
 * @param application aplicação ou tecnologia (ex.: "Navegador", "Planilha"); pode ser vazio
 * @param screenTitle título da página ou tela; pode ser vazio
 * @param location    endereço sanitizado (URL, caminho, endpoint); pode ser vazio
 */
public record StepContext(String application, String screenTitle, String location) {
    public StepContext {
        application = application == null ? "" : application;
        screenTitle = screenTitle == null ? "" : screenTitle;
        location = location == null ? "" : location;
    }
}
