package com.gongwen.assistant.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcAiSettingsRepository implements AiSettingsRepository {
    private static final long SINGLETON_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final AiSettingsSecretCodec secretCodec;

    public JdbcAiSettingsRepository(JdbcTemplate jdbcTemplate, AiSettingsSecretCodec secretCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretCodec = secretCodec;
    }

    @Override
    public Optional<AiSettingsSnapshot> find() {
        return jdbcTemplate.query("""
                        select provider,
                               deepseek_enabled,
                               deepseek_base_url,
                               deepseek_model,
                               deepseek_api_key_secret,
                               deepseek_timeout_seconds
                        from ai_provider_settings
                        where id = ?
                        """,
                (rs, rowNum) -> new AiSettingsSnapshot(
                        rs.getString("provider"),
                        rs.getBoolean("deepseek_enabled"),
                        rs.getString("deepseek_base_url"),
                        rs.getString("deepseek_model"),
                        secretCodec.decrypt(rs.getString("deepseek_api_key_secret")),
                        rs.getInt("deepseek_timeout_seconds")
                ),
                SINGLETON_ID).stream().findFirst();
    }

    @Override
    public void save(AiSettingsSnapshot settings) {
        jdbcTemplate.update("""
                        insert into ai_provider_settings (
                            id,
                            provider,
                            deepseek_enabled,
                            deepseek_base_url,
                            deepseek_model,
                            deepseek_api_key_secret,
                            deepseek_timeout_seconds,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, now())
                        on conflict (id) do update set
                            provider = excluded.provider,
                            deepseek_enabled = excluded.deepseek_enabled,
                            deepseek_base_url = excluded.deepseek_base_url,
                            deepseek_model = excluded.deepseek_model,
                            deepseek_api_key_secret = excluded.deepseek_api_key_secret,
                            deepseek_timeout_seconds = excluded.deepseek_timeout_seconds,
                            updated_at = now()
                        """,
                SINGLETON_ID,
                settings.provider(),
                settings.deepSeekEnabled(),
                settings.deepSeekBaseUrl(),
                settings.deepSeekModel(),
                secretCodec.encrypt(settings.deepSeekApiKey()),
                settings.deepSeekTimeoutSeconds());
    }
}
