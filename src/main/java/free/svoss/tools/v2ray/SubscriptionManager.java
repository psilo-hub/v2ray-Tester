package free.svoss.tools.v2ray;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class SubscriptionManager {

    private static final File subscriptionFile = new File(System.getProperty("user.home") + File.separator + ".v2ray-subscriptions.txt");
    private static final String DEFAULT_SUBSCRIPTION_URL = "https://openproxylist.com/v2ray/rawlist/text";

    private SubscriptionManager() {
    }

    static File getSubscriptionFile() {
        return subscriptionFile;
    }

    static void ensureDefaultSubscriptions() throws IOException {
        if (!subscriptionFile.exists()) {
            Set<String> urls = new LinkedHashSet<>();
            urls.add(DEFAULT_SUBSCRIPTION_URL);
            saveSubscriptions(urls);
            System.out.println("Created " + subscriptionFile.getAbsolutePath() + " with the default subscription");
        }
    }

    static Set<String> loadSubscriptions() throws IOException {
        Set<String> urls = new LinkedHashSet<>();
        for (String line : Files.readAllLines(subscriptionFile.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            // remove old v2nodes url: something like https://www.v2nodes.com/subscriptions/country/all/?key=001AA22DB6EDBA0
            if (!trimmed.isEmpty()&&!trimmed.contains("https://www.v2nodes.com/subscriptions/country/all/?key=")) urls.add(trimmed);
        }

        String v2nodesUrl = getFreshV2nodesUrl();
        if(v2nodesUrl!=null) urls.add(v2nodesUrl);

        return urls;
    }

    private static String getFreshV2nodesUrl() {
        return getV2nodesUrlFromDoc(Util.getJsoupDoc("https://www.v2nodes.com/"));
    }

    private static String getV2nodesUrlFromDoc( Document doc) {
        if(doc==null) return null;
        Element subscriptionInput = doc.selectFirst("input[id=subscription]");
        if(subscriptionInput==null) return null;
        String value = subscriptionInput.attr("value");
        String dataConfig=subscriptionInput.attr("data-config");
        if(value.contains("?key="))return value;
        if(dataConfig.contains("?key="))return dataConfig;
        return null;
    }

    static void saveSubscriptions(Set<String> urls) throws IOException {
        Util.atomicWrite(subscriptionFile, String.join(System.lineSeparator(), urls).getBytes(StandardCharsets.UTF_8));
    }

    static void addSubscription(String url) throws IOException {
        if (url == null) {
            System.err.println("Invalid subscription URL: null");
            return;
        }
        url = url.trim();
        if (!url.startsWith("https://")) {
            System.err.println("Invalid subscription URL (must start with https://): " + url);
            return;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host != null) {
                String lower = host.toLowerCase(Locale.ROOT);
                if (lower.equals("localhost") || lower.equals("127.0.0.1") || lower.equals("::1") || lower.equals("0.0.0.0") || lower.endsWith(".local")) {
                    System.err.println("Invalid subscription URL (localhost/loopback addresses are not allowed): " + url);
                    return;
                }
            }
        } catch (java.net.URISyntaxException e) {
            System.err.println("Invalid subscription URL: " + url + " (" + e.getMessage() + ")");
            return;
        }
        Set<String> urls = loadSubscriptions();
        if (urls.add(url)) {
            saveSubscriptions(urls);
            System.out.println("Added subscription: " + url);
        } else System.out.println("Subscription already present: " + url);
    }

    static void removeSubscription(String url) throws IOException {
        Set<String> urls = loadSubscriptions();
        if (urls.remove(url)) {
            saveSubscriptions(urls);
            System.out.println("Removed subscription: " + url);
        } else System.out.println("Subscription not found: " + url);
    }
}
