package com.gongwen.assistant.ai;

import java.util.Optional;

public interface AiSettingsRepository {
    Optional<AiSettingsSnapshot> find();

    void save(AiSettingsSnapshot settings);
}
