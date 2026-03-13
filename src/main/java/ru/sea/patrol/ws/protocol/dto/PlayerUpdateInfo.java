package ru.sea.patrol.ws.protocol.dto;

public record PlayerUpdateInfo(
        String name,
        int health,
        float velocity,
        int sailLevel,
        float x,
        float z,
        float angle) {
}
