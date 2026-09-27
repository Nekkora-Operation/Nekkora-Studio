package com.nekkora.tokens;

public enum FontWeight {
    REGULAR(400),
    MEDIUM(500),
    SEMIBOLD(600),
    BOLD(700);

    private final int value;

    FontWeight(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
