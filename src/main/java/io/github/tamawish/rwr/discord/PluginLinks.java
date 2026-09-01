package io.github.tamawish.rwr.discord;

/** Hard-coded project URLs (not exposed via config.yml). */
public final class PluginLinks {
    public static final String REPOSITORY = "https://github.com/TamaWish/RWR-Discord-Webhook";
    public static final String PRIVACY_POLICY = REPOSITORY + "/blob/main/Privacy%20Policy.md";
    public static final String TERMS_OF_SERVICE = REPOSITORY + "/blob/main/Terms%20of%20Service.md";

    private PluginLinks() {}

    public static String appendLegalLinks(String description, String legalLinksLine) {
        if (legalLinksLine == null || legalLinksLine.isBlank()) {
            return description == null ? "" : description;
        }
        if (description == null || description.isBlank()) {
            return legalLinksLine;
        }
        return description + "\n\n" + legalLinksLine;
    }
}
