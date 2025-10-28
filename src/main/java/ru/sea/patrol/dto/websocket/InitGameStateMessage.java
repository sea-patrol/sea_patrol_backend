package ru.sea.patrol.dto.websocket;

import java.util.List;

public record InitGameStateMessage(
        WindInfo wind,
        List<PlayerInfo> players) {
}
