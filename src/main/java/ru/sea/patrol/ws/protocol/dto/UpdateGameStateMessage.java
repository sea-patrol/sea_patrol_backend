package ru.sea.patrol.ws.protocol.dto;

import java.util.List;

public record UpdateGameStateMessage(
        float delta,
        WindInfo wind,
        List<PlayerUpdateInfo> players) {
}
