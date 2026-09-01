package io.github.tamawish.rwr.discord;

import io.github.tamawish.rwr.discord.command.DiscordCommand;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import io.github.tamawish.rwr.discord.locale.LocaleService;
import io.github.tamawish.rwr.discord.webhook.WebhookService;
import io.github.tamawish.rwr.discord.webhook.DeliveryStats;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import java.util.Optional;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Discord webhook add-on for ResourceWorldResetter.
 *
 * <p>Depends only on the public RWR API surface and Bukkit/Paper at compile time. Both RWR
 * runtime plugin names are soft dependencies; the add-on disables event delivery cleanly when the
 * API service is absent.
 */
public final class RwrDiscordPlugin extends JavaPlugin implements Listener {
    private static final String API_CLASS = "io.github.tamawish.rwr.api.RwrApi";
    private static final String LISTENER_CLASS =
            "io.github.tamawish.rwr.discord.listener.ResetEventListener";
    private static final String[] RUNTIME_PLUGINS = {
        "ResourceWorldResetter", "ResourceWorldResetter-Paper-Folia"
    };

    private DiscordConfig config;
    private LocaleService locale;
    private WebhookService webhooks;
    private final DeliveryStats deliveryStats = new DeliveryStats();
    private Listener listener;
    private volatile boolean apiAvailable;
    private static final int BSTATS_PLUGIN_ID = 33788;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_PLUGIN_ID);

        saveDefaultConfig();
        Optional<String> startupError = reloadServices();
        if (locale == null) {
            locale = new LocaleService(this);
            locale.reload("en_US");
        }

        PluginCommand command = getCommand("rwrdiscord");
        if (command != null) {
            DiscordCommand executor = new DiscordCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'rwrdiscord' missing from plugin.yml");
        }

        getServer().getPluginManager().registerEvents(this, this);
        refreshApiAvailability();
        String webhookState = webhooks != null && webhooks.isConfigured() ? "configured" : "disabled";
        getLogger().info(locale.format(
                apiAvailable ? "log.enabled" : "log.enabled-degraded", "webhook_state", webhookState));
        startupError.ifPresent(error -> getLogger().severe("RWR-Discord Webhook configuration is inactive: " + error));
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
        unregisterApiListener();
        HandlerList.unregisterAll((Listener) this);
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
            DiscordConfig candidateConfig = DiscordConfig.load(getConfig());
            LocaleService candidateLocale = new LocaleService(this);
            candidateLocale.reload(candidateConfig.locale());
            WebhookService candidateWebhooks =
                    new WebhookService(
                            this, candidateConfig, candidateLocale, deliveryStats, this::isApiAvailable);

            WebhookService previous = webhooks;
            DiscordConfig previousConfig = config;
            LocaleService previousLocale = locale;
            if (previous != null) {
                previous.shutdown();
            }
            apiAvailable = findApiService();
            try {
                candidateWebhooks.prepare();
                candidateWebhooks.start();
            } catch (RuntimeException activationError) {
                if (previousConfig != null && previousLocale != null) {
                    WebhookService rollback = new WebhookService(
                            this, previousConfig, previousLocale, deliveryStats, this::isApiAvailable);
                    rollback.prepare();
                    rollback.start();
                    webhooks = rollback;
                }
                throw activationError;
            }
            config = candidateConfig;
            locale = candidateLocale;
            webhooks = candidateWebhooks;
            refreshApiAvailability();
            if (candidateWebhooks.isConfigured()) {
                candidateWebhooks.enqueue(
                        NotificationCategory.CONFIGURATION,
                        "configuration",
                        candidateWebhooks.embeds().buildConfiguration(
                                apiAvailable,
                                getDescription().getVersion(),
                                getServer().getName() + " " + getServer().getVersion()));
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            detail = WebhookService.redact(detail);
            getLogger().severe("Failed to reload RWR-Discord Webhook: " + detail);
            return Optional.of(detail);
        }
    }

    /** Re-checks whether the public RWR API service is registered. */
    public void refreshApiAvailability() {
        apiAvailable = findApiService();
        if (apiAvailable) {
            registerApiListener();
        } else {
            unregisterApiListener();
        }
        if (webhooks != null) {
            webhooks.availabilityChanged();
        }
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

    public DeliveryStats deliveryStats() {
        return deliveryStats;
    }

    @EventHandler
    public void onRuntimeEnabled(PluginEnableEvent event) {
        if (isRuntimePlugin(event.getPlugin().getName())) {
            refreshApiAvailability();
        }
    }

    @EventHandler
    public void onRuntimeDisabled(PluginDisableEvent event) {
        if (isRuntimePlugin(event.getPlugin().getName())) {
            refreshApiAvailability();
        }
    }

    private boolean findApiService() {
        for (String pluginName : RUNTIME_PLUGINS) {
            Plugin runtime = getServer().getPluginManager().getPlugin(pluginName);
            if (runtime == null || !runtime.isEnabled()) {
                continue;
            }
            try {
                Class<?> apiType = Class.forName(API_CLASS, false, runtime.getClass().getClassLoader());
                if (hasRegistration(apiType)) {
                    return true;
                }
            } catch (ClassNotFoundException | LinkageError | SecurityException ex) {
                getLogger().fine("RWR runtime does not expose the 5.1 API: " + ex.getMessage());
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean hasRegistration(Class<?> apiType) {
        RegisteredServiceProvider<?> registration =
                getServer().getServicesManager().getRegistration((Class) apiType);
        return registration != null;
    }

    private void registerApiListener() {
        if (listener != null) {
            return;
        }
        try {
            Class<?> listenerType = Class.forName(LISTENER_CLASS, true, getClassLoader());
            listener = (Listener) listenerType.getConstructor(RwrDiscordPlugin.class).newInstance(this);
            getServer().getPluginManager().registerEvents(listener, this);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            listener = null;
            apiAvailable = false;
            getLogger().warning("RWR API event classes are unavailable; webhook event delivery remains disabled.");
        }
    }

    private void unregisterApiListener() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    private static boolean isRuntimePlugin(String name) {
        for (String runtime : RUNTIME_PLUGINS) {
            if (runtime.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
