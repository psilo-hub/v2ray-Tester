# v2ray-Tester

[![CI](https://github.com/psilo-hub/v2ray-Tester/actions/workflows/ci.yml/badge.svg)](https://github.com/psilo-hub/v2ray-Tester/actions/workflows/ci.yml)

A Java command-line tool that automatically fetches, parses, and benchmarks V2Ray / Xray server configurations. It measures TCP ping latency and real download speed through an Xray SOCKS5 proxy, then ranks servers with a composite score. The best server URL is exported as both a text list and a QR code image.

## Features

- **Multi-protocol support** — vmess, vless, trojan, shadowsocks (`ss://`), and hysteria2 (`hysteria2://`, `hy2://`) server URLs
- **Multi-source fetching** — subscription URLs and [freev2ray.cc](https://freev2ray.cc/) scraper
- **Automated Xray core management** — downloads and extracts the correct Xray binary for your platform from GitHub releases (or uses a locally installed one via `V2RAY_CORE` env / `v2ray.core` property)
- **Concurrent ping & download testing** — two-phase pipeline: TCP connect ping first, then real download speed through the Xray SOCKS5 proxy
- **Composite scoring** — ranks servers by `(5000 - ping_ms) * speed_MB/s`, balancing latency and throughput
- **QR code output** — generates `best.png` and an ASCII QR code in the terminal for easy import into mobile clients
- **Subscription management** — add, remove, and list subscription URLs from the command line
- **Graceful shutdown** — Ctrl+C writes partial results instead of losing progress
- **Cross-platform** — works on Windows and Linux/macOS with included launcher scripts
- **Windows-friendly console** — forces UTF-8 output on Windows consoles so non-ASCII server remarks render correctly
- **Persistent server cache** — tested servers are saved to `~/v2rayservers.json` and reused on subsequent runs

## Requirements

- **Java 8** or newer (JRE or JDK). [Eclipse Temurin](https://adoptium.net/temurin/releases/) is a good free option.
- **Maven 3.6+** (only needed to build from source)
- **Internet connection** (for fetching subscriptions and downloading the Xray core)

## Quick Start (End Users)

1. Download the latest release bundle from the [GitHub Releases](https://github.com/psilo-hub/v2ray-Tester/releases/latest) page.
2. Keep the JAR and the launcher script together in the same folder.
3. Run it:

**Windows:** double-click `start.bat`

**Linux / macOS:**
```bash
chmod +x start.sh
./start.sh
```

The launchers check that Java is installed and that `v2ray-tester-1.0.0.jar` sits next to them before starting.

## Building from Source

```bash
# Compile, run tests, and package
mvn clean package
```

This produces a fat JAR at:

```
target/v2ray-tester-1.0.0.jar
```

Run it directly after building:

```bash
java -jar target/v2ray-tester-1.0.0.jar
```

> **Note:** the launcher scripts in `scripts/` expect the JAR in their own folder — they are meant for the release layout above, not for running straight from a source checkout. Use `java -jar target/...` after a fresh build instead.

## Adding More Subscription Sources

The tool comes with a default subscription, but you can add your own:

```bash
java -jar target/v2ray-tester-1.0.0.jar --add "https://example.com/v2ray-sub"
```

See [docs/subscription_list.txt](docs/subscription_list.txt) for a list of known subscription sources. A web search will also reveal many more, and a good starting point is the [v2ray-config topic on GitHub](https://github.com/topics/v2ray-config).

### What Happens on First Run

1. A default subscription file is created at `~/.v2ray-subscriptions.txt` with the [openproxylist](https://openproxylist.com/v2ray/rawlist/text) source.
2. Server configs are fetched from subscriptions and [freev2ray.cc](https://freev2ray.cc/).
3. Each server is pinged and, if reachable, download-tested through an Xray SOCKS5 proxy.
4. Results are written next to the JAR:
   - `all.txt` — raw URLs of all servers that passed the speed test
   - `best.txt` — raw URLs of the top-ranked servers
   - `best.png` — QR code of the single best server URL

## Command-Line Options

```
Usage: java -jar v2ray-tester-1.0.0.jar [options]

Options:
  --help             Print help and exit
  --version          Print version and exit
  --add <url>        Add a subscription URL and exit
  --remove <url>     Remove a subscription URL and exit
  --list             List all subscription URLs and exit
  --no-fetching      Test stored server configs without fetching new ones
  --just-fetch       Fetch and save server configs without testing

Exit codes:
  0  Success — at least one server passed the speed test
  1  Error (invalid arguments, missing output directory, etc.)
  2  Partial success — subscriptions fetched but no servers passed
```

### Examples

```bash
# Add a subscription
java -jar v2ray-tester-1.0.0.jar --add "https://example.com/v2ray-sub"

# List saved subscriptions
java -jar v2ray-tester-1.0.0.jar --list

# Remove a subscription
java -jar v2ray-tester-1.0.0.jar --remove "https://example.com/v2ray-sub"

# Test only previously saved servers (no network fetching)
java -jar v2ray-tester-1.0.0.jar --no-fetching

# Fetch configs only (skip testing)
java -jar v2ray-tester-1.0.0.jar --just-fetch
```

## Helper Scripts (`scripts/`)

| Script | Purpose |
|--------|---------|
| `start.bat` / `start.sh` | Launch the app (checks Java and the JAR first; see Quick Start) |
| `addOrRemoveSubscriptionUrls.bat` / `.sh` | Manage the entries in your subscription URL list |

## How It Works

### Testing Pipeline

```
┌─────────────────────┐
│  Fetch Subscriptions│  Subscription URLs + freev2ray.cc
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Parse Server URLs  │  vmess://, vless://, trojan://, ss://, hysteria2:// → ServerConfig objects
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Deduplicate & Save │  Persisted to ~/v2rayservers.json
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Phase 1: TCP Ping  │  Concurrent socket connect to measure latency (2s timeout)
└────────┬────────────┘
         │ only reachable servers
         ▼
┌─────────────────────────────────┐
│  Phase 2: Download Speed Test   │  Xray core as SOCKS5 proxy → download 1 MB test file
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────┐
│  Score & Rank Servers   │  score = (5000 - ping) × speed
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  Output Results         │  all.txt, best.txt, best.png, terminal QR code
└─────────────────────────┘
```

### Xray Core

The speed test works by running a local [Xray-core](https://github.com/XTLS/Xray-core) process with a generated config that routes traffic through the server under test. The tool then downloads a 1 MB test file through the local SOCKS5 proxy and measures throughput (primary endpoint: Cloudflare speed test, with public mirrors as fallbacks).

The core binary is resolved in this order:
1. `V2RAY_CORE` environment variable (path to the executable)
2. `v2ray.core` system property
3. A bundled zip inside the JAR (if present)
4. Automatic download from GitHub releases (`https://github.com/XTLS/Xray-core/releases/latest`) into a local `xray-core/` directory next to the JAR

### Server Scoring

Servers are ranked by a composite score:

```
score = (SCORE_PING_CEILING - ping_ms) × speed_MB/s
```

Where `SCORE_PING_CEILING = 5000 ms`. This means:
- Lower ping gives a higher base component
- Speed directly multiplies the ping score
- A server with slightly higher ping but significantly faster download can outrank a low-ping, slow server

## Project Structure

```
v2ray-tester/
├── src/
│   ├── main/java/free/svoss/tools/v2ray/
│   │   ├── Ansi.java                        # ANSI escape sequences (bold, reset, clear screen)
│   │   ├── App.java                         # Main entry point, orchestration, CLI parsing
│   │   ├── CliArgs.java                     # Command-line argument helpers
│   │   ├── FreeV2RayCcScraper.java          # Scraper for freev2ray.cc
│   │   ├── JarFolderTool.java               # Locates the running JAR's folder
│   │   ├── Parser.java                      # Parses vmess/vless/trojan/ss/hysteria2 URLs
│   │   ├── QrHelper.java                    # QR code generation (PNG + ASCII)
│   │   ├── ResultReporter.java              # Writes all.txt / best.txt / best.png output
│   │   ├── ServerConfig.java                # Immutable server config model (Builder pattern)
│   │   ├── ServerConfigWithTestResult.java  # Wraps config with ping/speed results
│   │   ├── SpeedTester.java                 # Xray core download speed measurement
│   │   ├── SubscriptionManager.java         # Manages ~/.v2ray-subscriptions.txt
│   │   ├── Util.java                        # Shared utilities (atomicWrite, retryNetwork)
│   │   ├── WindowsConsoleSetUnicodeOutput.java  # UTF-8 console output on Windows (via JNA)
│   │   ├── XrayConfigBuilder.java           # Generates Xray JSON config from ServerConfig
│   │   └── XrayCoreManager.java             # Downloads, extracts, and caches the Xray binary
│   ├── main/resources/
│   │   └── version.properties               # Version number (injected by Maven)
│   └── test/java/free/svoss/tools/v2ray/
│       ├── AppCalcScoreTest.java            # Tests for composite score calculation
│       ├── AppIsPrivateHostTest.java        # Tests for private/internal host detection
│       ├── Hysteria2ParserTest.java         # Tests for hysteria2/hy2 URL parsing
│       ├── ParserTest.java                  # Tests for URL parsing (vmess, vless, trojan, ss)
│       ├── ServerConfigBuilderValidationTest.java  # Builder validation tests
│       └── ServerConfigWithTestResultTest.java     # Round-trip serialization tests
├── docs/
│   └── subscription_list.txt                # Known free subscription sources
├── scripts/
│   ├── start.bat                            # Windows launcher (expects JAR beside it)
│   ├── start.sh                             # Linux/macOS launcher (expects JAR beside it)
│   ├── rebuild.bat                          # Windows: rebuild the JAR
│   ├── rebuildAndStart.bat                  # Windows: rebuild and run
│   ├── addOrRemoveSubscriptionUrls.bat      # Manage subscription URLs (interactive)
│   └── addOrRemoveSubscriptionUrls.sh       # Manage subscription URLs (interactive)
├── .github/workflows/
│   ├── ci.yml                               # Build + test matrix (Ubuntu, Windows)
│   └── release.yml                          # Publishes a GitHub Release on v* tags
└── pom.xml                                  # Maven build config
```

## Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `~/.v2ray-subscriptions.txt` | User home | One subscription URL per line |
| `~/v2rayservers.json` | User home | Cached server configs (JSON) |

## Testing

```bash
# Run unit tests
mvn test
```

The test suite covers:
- `ServerConfig` builder validation and JSON round-trip serialization
- TCP ping score calculation (`calcScore`)
- Private/internal host detection (`isPrivateHost`)
- URL parsing for all five protocols (vmess, vless, trojan, shadowsocks, hysteria2)

## CI

GitHub Actions builds and tests on pushes and PRs to `main`, using a matrix across operating systems:

```yaml
# .github/workflows/ci.yml
strategy:
  matrix:
    os: [ubuntu-latest, windows-latest]
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      java-version: '8'
      distribution: 'temurin'
  - run: mvn -B clean verify
```

## Releases

Pushing a tag matching `v*` triggers [.github/workflows/release.yml](.github/workflows/release.yml), which builds the fat JAR and automatically publishes a GitHub Release containing:

- `v2ray-tester-1.0.0.jar` — the application
- `start.bat` / `start.sh` — launchers (see Quick Start)
- `subscription_list.txt` — known subscription sources
- `addOrRemoveSubscriptionUrls.bat` / `.sh` — subscription management helpers

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| [Gson](https://github.com/google/gson) | 2.14.0 | JSON serialization/deserialization |
| [Jsoup](https://jsoup.org/) | 1.23.1 | HTTP fetching and HTML parsing (subscriptions, freev2ray.cc) |
| [ZXing](https://github.com/zxing/zxing) | 3.5.4 | QR code generation (PNG + ASCII terminal rendering) |
| [Jansi](https://fusesource.github.io/jansi/) | 2.4.2 | ANSI escape handling for terminal output |
| [JNA](https://github.com/java-native-access/jna) | 5.17.0 | Native call to enable UTF-8 console output on Windows |
| [JUnit 5](https://junit.org/junit5/) | 5.14.4 | Unit testing (test scope) |

## License

No license has been published yet — all rights reserved by the author. This section should be updated once a `LICENSE` file is added to the repository.
