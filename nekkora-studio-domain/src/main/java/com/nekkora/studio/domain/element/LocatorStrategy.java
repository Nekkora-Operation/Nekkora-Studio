package com.nekkora.studio.domain.element;

/**
 * Estratégias de localização — mesmos valores do enum {@code strategy} do PageMap 0.2.0
 * ({@code contracts/page-map.schema.json} da Chrome Extension). Compatibilidade verificada por teste.
 */
public enum LocatorStrategy {
    TEST_ATTRIBUTE, ID, NAME, LABEL, ROLE_NAME, CONTEXTUAL, ROW_CONTEXT, ATTRIBUTE, TEXT, XPATH_RELATIVE, POSITIONAL
}
