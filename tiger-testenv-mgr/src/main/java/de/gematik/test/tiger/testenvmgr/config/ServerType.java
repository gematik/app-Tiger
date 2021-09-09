package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ServerType {
    DOCKER("docker"), DOCKER_COMPOSE("compose"),
    EXTERNALJAR("externalJar"), EXTERNALURL("externalUrl"),
    REVERSEPROXY("reverseProxy");

    private final String name;

    ServerType(String nm) {
        this.name = nm;
    }

    @JsonValue
    public String getName() {
        return name;
    }
}
