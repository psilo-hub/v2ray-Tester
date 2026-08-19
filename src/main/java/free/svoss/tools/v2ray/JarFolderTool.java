package free.svoss.tools.v2ray;


import java.io.File;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.util.Locale;

public class JarFolderTool {

    public static File getRunningFromFolder() {
        File jar = getJarFileOrFolder();
        if (jar != null) {
            if (jar.isFile()) return jar.getParentFile();
            if (jar.isDirectory()) return jar;
        }
        System.err.println("Warning: Could not determine jar location, using current directory as fallback");
        String userDir = System.getProperty("user.dir");
        if (userDir == null) {
            System.err.println("Error: user.dir system property is null. Cannot determine fallback directory.");
            return null;
        }
        return new File(userDir);
    }

    public static File getJarFileOrFolder() {

        URI uri = null;
        try {
            // Use the class containing this method to obtain the code source.
            // (You may replace JarUtils.class with any class known to be in your main JAR.)
            CodeSource codeSource = App.class.getProtectionDomain().getCodeSource();
            URL location = codeSource == null ? null : codeSource.getLocation();

            // Convert URL to URI to properly decode any percent-encoded characters.
            uri = location == null ? null : location.toURI();
        } catch (Exception e) {
            System.err.println("Failed to get jar location : " + e.getMessage());
            return null;
        }
        if (uri == null) return null;
        File file = new File(uri);

        // Verify that it is a file and that its name ends with ".jar" (case-insensitive).
        if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
            return file;
        if (file.isDirectory() && "classes".equals(file.getName().toLowerCase(Locale.ROOT))) {
            File parent = file.getParentFile();
            if (parent != null && parent.isDirectory() && "target".equals(parent.getName().toLowerCase(Locale.ROOT)))
                return parent.getParentFile();
        }
        return null; // Not a JAR file (e.g., a directory or a module)

    }

}
