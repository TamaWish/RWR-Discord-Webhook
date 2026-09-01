package io.github.tamawish.rwr.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test
    void declaresFoliaSupportAndResolvedVersion() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertThat(input).isNotNull();
            String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(descriptor).contains("name: RWR-Discord-Webhook");
            assertThat(descriptor).contains("folia-supported: true");
            assertThat(descriptor).contains("Spigot, CraftBukkit, Paper, Purpur, and Folia");
            assertThat(descriptor).contains("version: '1.0.0'");
            assertThat(descriptor).doesNotContain("${project.version}");
            assertThat(descriptor).contains("website: https://github.com/TamaWish/RWR-Discord-Webhook");
        }
    }
}
