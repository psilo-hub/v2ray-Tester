package free.svoss.tools.v2ray;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ResultReporter {

    static void writeBestServersToFile(List<ServerConfigWithTestResult> bySpeed, List<ServerConfigWithTestResult> bestSorted, File outDir) throws IOException {
        List<String> allLines = new ArrayList<>();
        for (ServerConfigWithTestResult r : bySpeed) r.getServerConfig().ifPresent(sc -> allLines.add(sc.getRawUrl()));
        writeLines(new File(outDir, "all.txt"), allLines);

        List<String> bestLines = new ArrayList<>();
        for (ServerConfigWithTestResult r : bestSorted) r.getServerConfig().ifPresent(sc -> bestLines.add(sc.getRawUrl()));
        writeLines(new File(outDir, "best.txt"), bestLines);

        ServerConfigWithTestResult best = bestSorted.isEmpty() ? bySpeed.get(0) : bestSorted.get(0);
        best.getServerConfig().ifPresent(sc -> QrHelper.saveQrPng(sc.getRawUrl(), new File(outDir, "best.png")));
    }

    static void printBestServerSummary(List<ServerConfigWithTestResult> bySpeed, List<ServerConfigWithTestResult> bestSorted, File outDir) {
        ServerConfigWithTestResult best = bestSorted.isEmpty() ? bySpeed.get(0) : bestSorted.get(0);
        File pngFile = new File(outDir, "best.png");

        System.out.println();
        System.out.println("Results written to " + outDir.getAbsolutePath());
        System.out.println("  all.txt:  " + bySpeed.size() + " servers (passed the speed test)");
        System.out.println("  best.txt: " + bestSorted.size() + " best servers");
        System.out.println("  best.png: " + pngFile.getAbsolutePath());
        System.out.println();
        System.out.println("Best server: " + String.format(Locale.ROOT, "%.2f", best.getSpeedMbPerSecond()) + " MB/s, " + best.getPing() + " ms");
        best.getServerConfig().ifPresent(sc -> {
            System.out.println(sc.getRawUrl());
            System.out.println(QrHelper.renderQrAscii(sc.getRawUrl()));
        });
    }

    static void writeLines(File file, List<String> lines) throws IOException {
        Files.write(file.toPath(), String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }
}
