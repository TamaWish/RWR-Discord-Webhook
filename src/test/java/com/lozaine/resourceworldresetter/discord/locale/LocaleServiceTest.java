package com.lozaine.resourceworldresetter.discord.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocaleServiceTest {
    @Test
    void stripMentionsRemovesUserRoleAndMassMentions() {
        assertThat(LocaleService.stripMentions("Hello <@12345> and <@!678> and <@&999>"))
                .isEqualTo("Hello [user] and [user] and [role]");
        assertThat(LocaleService.stripMentions("Ping @everyone and @here"))
                .contains("@\u200Beveryone")
                .contains("@\u200Bhere")
                .doesNotContain("@everyone ")
                .doesNotContain("@here");
    }

    @Test
    void stripMentionsHandlesNullAndEmpty() {
        assertThat(LocaleService.stripMentions(null)).isEmpty();
        assertThat(LocaleService.stripMentions("")).isEmpty();
    }
}
