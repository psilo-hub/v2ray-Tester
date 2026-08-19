package free.svoss.tools.v2ray;

class CliArgs {

    static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equals(a)) return true;
        }
        return false;
    }

    static String argValue(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i]) && i + 1 < args.length) return args[i + 1];
        }
        return null;
    }

    static void printUsage(String appVersion) {
        System.out.println("Usage: java -jar v2ray-tester-" + appVersion + "-jar-with-dependencies.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help             Print this help and exit");
        System.out.println("  --version          Print version and exit");
        System.out.println("  --add <url>        Add a subscription url and exit");
        System.out.println("  --remove <url>     Remove a subscription url and exit");
        System.out.println("  --list             List all subscription urls and exit");
        System.out.println("  --long-run         Also fetch server configs from the ebrasha public list");
        System.out.println("  --no-fetching      Test stored server configs without fetching new ones");
        System.out.println("  --just-fetch       Fetch and save server configs without testing");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  Success (at least one server passed the speed test)");
        System.out.println("  1  Error (invalid arguments, missing output directory, etc.)");
        System.out.println("  2  Partial success (subscriptions fetched but no servers passed)");
        System.out.println();
        System.out.println("Without options the app fetches configs from the hardcoded https://freev2ray.cc/");
        System.out.println("and the subscriptions in " + SubscriptionManager.getSubscriptionFile().getAbsolutePath());
        System.out.println("tests them and writes all.txt / best.txt / best.png next to the jar.");
    }
}
