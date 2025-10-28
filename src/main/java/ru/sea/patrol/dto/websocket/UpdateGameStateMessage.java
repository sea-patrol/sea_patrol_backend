package ru.sea.patrol.dto.websocket;

import java.util.List;

public record UpdateGameStateMessage(
        float delta,
        WindInfo wind,
        List<PlayerUpdateInfo> players) {
}
