package ru.sea.patrol.dto.websocket;

public record PlayerUpdateInfo(
        String name,
        int health,
        float velocity,
        float x,
        float z,
        float angle) {
}
