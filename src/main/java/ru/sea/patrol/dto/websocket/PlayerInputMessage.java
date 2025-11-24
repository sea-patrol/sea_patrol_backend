package ru.sea.patrol.dto.websocket;

public record PlayerInputMessage(Boolean left, Boolean right, Boolean up, Boolean down) {
}
