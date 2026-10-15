package com.nekkora.studio.bridge.json;

/** JSON malformado ou não representável. */
public final class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }
}
