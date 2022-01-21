package de.gematik.test.tiger.common.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TigerProxyType {
    HTTP("http"), HTTPS("https");

    private final String name;

    TigerProxyType(String nm) {
        this.name = nm;
    }

    @JsonValue
    public String getName() {
        return name;
    }
}
