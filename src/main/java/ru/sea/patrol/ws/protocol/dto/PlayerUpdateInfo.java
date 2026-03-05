package ru.sea.patrol.ws.protocol.dto;

public record PlayerUpdateInfo(
        String name,
        int health,
        float velocity,
        float x,
        float z,
        float angle) {
}
