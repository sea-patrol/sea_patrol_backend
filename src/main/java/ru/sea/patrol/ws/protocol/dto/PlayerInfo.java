package ru.sea.patrol.ws.protocol.dto;

public record PlayerInfo(
        String name,
        int health,
        int maxHealth,
        float velocity,
        int sailLevel,
        float x,
        float z,
        float angle,
        String model,
        float height,
        float width,
        float length) {
}
