package io.github.tamawish.rwr.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PluginLinksTest {
    @Test
    void pointsAtPublicRepositoryDocs() {
        assertThat(PluginLinks.REPOSITORY)
                .isEqualTo("https://github.com/TamaWish/RWR-Discord-Webhook");
        assertThat(PluginLinks.PRIVACY_POLICY)
                .isEqualTo(
                        "https://github.com/TamaWish/RWR-Discord-Webhook/blob/main/Privacy%20Policy.md");
        assertThat(PluginLinks.TERMS_OF_SERVICE)
                .isEqualTo(
                        "https://github.com/TamaWish/RWR-Discord-Webhook/blob/main/Terms%20of%20Service.md");
    }

    @Test
    void appendLegalLinksPreservesDescription() {
        String line = "[Privacy Policy](https://example.com/privacy)";
        assertThat(PluginLinks.appendLegalLinks("Ready.", line))
                .isEqualTo("Ready.\n\n[Privacy Policy](https://example.com/privacy)");
        assertThat(PluginLinks.appendLegalLinks("", line)).isEqualTo(line);
        assertThat(PluginLinks.appendLegalLinks("Ready.", "")).isEqualTo("Ready.");
    }
}
