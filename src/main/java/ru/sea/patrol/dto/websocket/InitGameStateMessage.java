package ru.sea.patrol.dto.websocket;

import java.util.List;

public record InitGameStateMessage(
        String room,
        WindInfo wind,
        List<PlayerInfo> players) {
}
