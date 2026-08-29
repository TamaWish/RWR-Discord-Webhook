package com.lozaine.resourceworldresetter.discord;

import com.lozaine.resourceworldresetter.api.RwrApi;
import com.lozaine.resourceworldresetter.discord.command.DiscordCommand;
import com.lozaine.resourceworldresetter.discord.config.DiscordConfig;
import com.lozaine.resourceworldresetter.discord.listener.ResetEventListener;
import com.lozaine.resourceworldresetter.discord.locale.LocaleService;
import com.lozaine.resourceworldresetter.discord.webhook.WebhookService;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Discord webhook add-on for ResourceWorldResetter.
 *
 * <p>Depends only on the public {@link RwrApi} surface and Bukkit/Paper at compile time. Both RWR
 * runtime plugin names are soft dependencies; the add-on disables event delivery cleanly when the
 * API service is absent.
 */
public final class RwrDiscordPlugin extends JavaPlugin {
    private DiscordConfig config;
    private LocaleService locale;
    private WebhookService webhooks;
    private ResetEventListener listener;
    private boolean apiAvailable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadServices();

        PluginCommand command = getCommand("rwrdiscord");
        if (command != null) {
            DiscordCommand executor = new DiscordCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'rwrdiscord' missing from plugin.yml");
        }

        listener = new ResetEventListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        refreshApiAvailability();
        String webhookState = webhooks != null && webhooks.isConfigured() ? "configured" : "disabled";
        getLogger().info(locale.format("log.enabled", "webhook_state", webhookState));
        if (!apiAvailable) {
            getLogger().warning(locale.raw("log.disabled-no-api"));
        }
        if (webhooks != null && !webhooks.isConfigured()) {
            getLogger().info(locale.raw("log.webhook-missing"));
        }
    }

    @Override
    public void onDisable() {
        if (webhooks != null) {
            webhooks.shutdown();
            webhooks = null;
        }
        listener = null;
        apiAvailable = false;
    }

    /**
     * Reloads config, locale, and webhook client from the data folder.
     *
     * @return empty on success, otherwise a short failure reason
     */
    public Optional<String> reloadServices() {
        try {
            reloadConfig();
            config = DiscordConfig.load(getConfig());
            if (locale == null) {
                locale = new LocaleService(this);
            }
            locale.reload(config.locale());
            if (webhooks != null) {
                webhooks.shutdown();
            }
            webhooks = new WebhookService(this, config, locale);
            refreshApiAvailability();
            return Optional.empty();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Failed to reload RWR-Discord", ex);
            return Optional.of(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    /** Re-checks whether {@link RwrApi} is registered. */
    public void refreshApiAvailability() {
        apiAvailable = RwrApi.find(getServer()).isPresent();
    }

    public boolean isApiAvailable() {
        return apiAvailable;
    }

    public DiscordConfig discordConfig() {
        return config;
    }

    public LocaleService locale() {
        return locale;
    }

    public WebhookService webhooks() {
        return webhooks;
    }
}
