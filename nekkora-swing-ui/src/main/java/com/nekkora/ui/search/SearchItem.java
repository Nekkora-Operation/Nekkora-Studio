package com.nekkora.ui.search;

import com.nekkora.ui.icon.IconName;

/**
 * Resultado de busca genérico (o domínio fica no {@code payload}).
 *
 * @param title    texto principal
 * @param subtitle contexto (caminho, tipo, descrição)
 * @param kind     rótulo do tipo, exibido como badge (ex.: "Projeto")
 * @param icon     ícone do tipo
 * @param payload  objeto da aplicação
 */
public record SearchItem(String title, String subtitle, String kind, IconName icon, Object payload) {}
