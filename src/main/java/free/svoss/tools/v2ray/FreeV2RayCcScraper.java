package free.svoss.tools.v2ray;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

class FreeV2RayCcScraper {

    static Set<String> getFreev2rayCcUrls() {
        String baseUrl = "https://freev2ray.cc/";
        Document doc = getJsoupDoc(baseUrl);
        if (doc == null) {
            System.err.println("Failed to fetch " + baseUrl);
            return new HashSet<>();
        }
        doc.select("script,head,nav").remove();
        Element panelBody = doc.selectFirst("main > div[class=container] > div[class=row] > div[class^=col] > div[class^=panel] > div[class=panel-body]");
        if (panelBody == null) {
            System.err.println("panelBody not found on " + baseUrl);
            return new HashSet<>();
        }
        Elements anchors = panelBody.select("a[href^=/free-node/]");
        Set<String> urls = new HashSet<>();
        for (Element a : anchors)
            urls.add(a.absUrl("href"));

        urls = filterFreeV2rayUrlsByDate(urls);

        Set<String> importUrls = new HashSet<>();
        for (String url : urls) {
            importUrls.addAll(getFreev2rayCcUrlsFromDayPage(url));
        }
        return importUrls;
    }

    static Set<String> filterFreeV2rayUrlsByDate(Set<String> urls) {
        urls.remove(null);
        if (urls.isEmpty()) return urls;
        LocalDate now = LocalDate.now();
        LocalDate yesterday = now.minusDays(1);
        String dateStringNow = now.format(DateTimeFormatter.ofPattern("yyyy-M-d"));
        String dateStringYesterday = yesterday.format(DateTimeFormatter.ofPattern("yyyy-M-d"));
        Set<String> urlsMatchingDay = urls.stream().filter(url -> url.contains(dateStringNow)).collect(Collectors.toSet());
        if (urlsMatchingDay.isEmpty())
            urlsMatchingDay = urls.stream().filter(url -> url.contains(dateStringYesterday)).collect(Collectors.toSet());
        return urlsMatchingDay.isEmpty() ? urls : urlsMatchingDay;
    }

    static Set<String> getFreev2rayCcUrlsFromDayPage(String url) {
        System.out.println("Day page : " + url);
        Document doc = null;
        try {
            doc = getJsoupDoc(url);
        } catch (Exception e) {
            System.err.println("Failed to fetch content from " + url + "\n" + e.getMessage() + "\n");
        }
        if (doc == null) {
            System.err.println("Failed to fetch content from " + url + "\n");
            return new HashSet<>();
        }
        Elements paragraphs = doc.select("p");
        Set<String> collectedSourceUrls = new HashSet<>();
        for (Element p : paragraphs) {
            String text = p.ownText();
            if (text.startsWith("https://node.freev2ray.cc/uploads/") && text.endsWith(".txt"))
                collectedSourceUrls.add(text);
        }
        if (collectedSourceUrls.isEmpty()) System.err.println("No urls collected from " + url);
        return collectedSourceUrls;
    }

    static Document getJsoupDoc(String url) {
        return Util.retryNetwork(() -> Jsoup.connect(url).maxBodySize(75 * 1024 * 1024).timeout(5 * 60 * 1000).get(), "get " + url, 3);
    }
}
