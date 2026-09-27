package com.nekkora.ui.icon;

/** Categorias do conjunto de ícones (organização do showcase e da documentação). */
public enum IconCategory {
    NAVIGATION("Navegação"),
    ACTIONS("Ações"),
    SERVICES("Serviços"),
    LAYOUT("Layout"),
    ARROWS("Setas"),
    STATUS("Status"),
    THEME("Tema"),
    OBJECTS("Objetos"),
    BRANDS("Marcas");

    private final String label;

    IconCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
