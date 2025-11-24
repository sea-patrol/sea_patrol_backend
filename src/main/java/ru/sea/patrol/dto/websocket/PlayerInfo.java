package ru.sea.patrol.dto.websocket;

public record PlayerInfo(
        String name,
        int health,
        int maxHealth,
        float velocity,
        float x,
        float z,
        float angle,
        String model,
        float height,
        float width,
        float length) {
}
