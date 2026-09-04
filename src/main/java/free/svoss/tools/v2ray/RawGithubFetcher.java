package free.svoss.tools.v2ray;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class RawGithubFetcher {
    private final static String rghucc = "raw.githubusercontent.com";

    public static byte[] get(String url) {
        if (!isRawGithubLink(url)) return null;

        byte[] data = null;

        //*// direct dl
        try {
            data = directDl(url);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (data != null && data.length > 0) return data;
        //*/

        //*// resolve ip and download
        try {
            data = resolveIpDl(url);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (data != null && data.length > 0) return data;
        //*/

        //*// mirror dl
        try {
            data = mirrorDl(url);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (data != null && data.length > 0) return data;
        //*/


        return null;
    }

    private static byte[] mirrorDl(String url) {
        // sadly I haven't gotten these to work yet:
        // https://www.jsdelivr.com/?docs=gh
        // https://fcp7.com/github-mirror-daily-updates.html
        return null;
    }

    private static byte[] resolveIpDl(String url) {

        HashSet<String> failedIps = new HashSet<>();
        LinkedList<String> resolvers = new LinkedList<>();
        Collections.addAll(resolvers,
                "https://gitee.com/if-the-wind/github-hosts/raw/main/hosts",
                "https://hosts.gitcdn.top/hosts.txt",
                "https://gitee.com/godfather1103/github-hosts/raw/master/hosts",
                "https://www.ipaddress.com/website/" + rghucc + "/"
        );

        byte[] data = null;
        while ((data == null || data.length == 0) && !resolvers.isEmpty())
            data = resolveIpDl(url, resolvers.removeFirst(), failedIps);


        return data;
    }

    private static byte[] resolveIpDl(String url, String resolver, HashSet<String> failedIps) {
        //System.out.println("\n\nresolver : " + resolver);
        Set<String> ips = fetchIps(resolver);
        if (ips == null) return null;
        ips.removeAll(failedIps);
        if (ips.isEmpty()) return null;

        for (String ip : ips) {
            //System.out.println("ip : " + ip);
            byte[] data = dlFromIp(url, ip);
            if (data == null || data.length == 0) failedIps.add(ip);
            else return data;
        }
        return null;
    }

    private static byte[] dlFromIp(String url, String ip) {
        byte[] output = null;
        String oldValue = System.getProperty("jdk.net.hosts.file");
        try {
            File tempHostsFile = new File(System.getProperty("user.home") + File.separator + "rawGithub_hosts");
            String content = ip + "       " + rghucc;
            Files.write(tempHostsFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
            System.setProperty("jdk.net.hosts.file", tempHostsFile.toString());
            output = readFromUrl(url);
        } catch (Exception ignored) {
        }
        if (oldValue != null)
            System.setProperty("jdk.net.hosts.file", oldValue);
        return output;
    }

    private static Set<String> fetchIps(String resolver) {
        byte[] content = readFromUrl(resolver);
        if (content == null || content.length == 0) return null;
        String s = new String(content, StandardCharsets.UTF_8);
        return s.toLowerCase(Locale.ROOT).contains("<html") ? extractFromHtml(s) : extractFromHosts(s);
    }

    private static Set<String> extractFromHtml(String s) {
        if (s == null || !s.contains("The website is associated with the network IP addresses")) return null;
        s = s.substring(s.indexOf("The website is associated with the network IP addresses") + "The website is associated with the network IP addresses".length()).trim();

        s = s.substring(0, s.indexOf("</"));
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();
        String[] token = s.split(",");
        Set<String> ips = new HashSet<>();
        for (String t : token) {
            t = t.trim();
            if (isIp4(t))
                ips.add(t);
        }
        return ips;
    }

    private static Set<String> extractFromHosts(String s) {
        if (s == null || s.isEmpty()) return null;
        Set<String> ips = new HashSet<>();
        for (String line : s.split("\n")) {
            if (line.contains(rghucc)) {
                line = line.replace(rghucc, " ").trim();
                while (line.length() > 7 && !Character.isDigit(line.substring(line.length() - 1).charAt(0)))
                    line = line.substring(0, line.length() - 1).trim();

                if (isIp4(line))
                    ips.add(line);
                else System.out.println("Not an ip: " + line);
            }
        }
        return ips;
    }

    private static boolean isIp4(String s) {
        if (s == null || s.length() < 7) return false;
        String[] token = s.split("\\.");
        if (token.length != 4) return false;

        for (String t : token)
            try {
                int i = Integer.parseInt(t);
                if (i < 0 || i > 255) return false;
            } catch (Exception ignored) {
                return false;
            }

        return true;
    }

    private static byte[] directDl(String url) {
        return readFromUrl(url);
    }

    private static byte[] readFromUrl(String url) {
        try (InputStream is = new URL(url).openStream()) {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[1024];

            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            return buffer.toByteArray();

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static boolean isRawGithubLink(String url) {
        if (url == null || !url.startsWith("https://" + rghucc + "/")) return false;
        url = url.substring(("https://" + rghucc + "/").length());
        if (url.isEmpty() || url.indexOf("/") == url.lastIndexOf("/")) return false;
        return true;
    }

    public static String getAsString(String url) {
        try {
            byte[] data = get(url);
            return data==null?null:new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
}

