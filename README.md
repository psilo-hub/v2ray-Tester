# v2ray-Tester

A Java command-line tool that automatically fetches, parses, and benchmarks V2Ray / Xray server configurations. It measures TCP ping latency and real download speed through an Xray SOCKS5 proxy, then ranks servers with a composite score. The best server URL is exported as both a text list and a QR code image.

## Features

- **Multi-protocol support** — vmess, vless, trojan, and shadowsocks (`ss://`) server URLs
- **Multi-source fetching** — subscription URLs and [freev2ray.cc](https://freev2ray.cc/) scraper
- **Automated Xray core management** — downloads and extracts the correct Xray binary for your platform from GitHub releases (or uses a locally installed one via `V2RAY_CORE` env / `v2ray.core` property)
- **Concurrent ping & download testing** — two-phase pipeline: TCP connect ping first, then real download speed through the Xray SOCKS5 proxy
- **Composite scoring** — ranks servers by `(5000 - ping_ms) * speed_MB/s`, balancing latency and throughput
- **QR code output** — generates `best.png` and an ASCII QR code in the terminal for easy import into mobile clients
- **Subscription management** — add, remove, and list subscription URLs from the command line
- **Graceful shutdown** — Ctrl+C writes partial results instead of losing progress
- **Cross-platform** — works on Windows and Linux/macOS with included launcher scripts
- **Persistent server cache** — tested servers are saved to `~/v2rayservers.json` and reused on subsequent runs

## Requirements

- **Java 8** or newer (JRE or JDK). [Eclipse Temurin](https://adoptium.net/temurin/releases/) is a good free option.
- **Maven 3.6+** (only needed to build from source)
- **Internet connection** (for fetching subscriptions and downloading the Xray core)

## Building from Source

```bash
# Compile, run tests, and package
mvn clean package
```

This produces a fat JAR at:

```
target/v2ray-tester-1.0.0-jar-with-dependencies.jar
```

## Running

### Quick Start

### Platform-Specific Launchers

**Windows:**
```
start.bat
```

**Linux / macOS:**
```bash
chmod +x start.sh
./start.sh
```

### Direct Execution

```bash
java -jar target/v2ray-tester-1.0.0-jar-with-dependencies.jar
```

### Adding More Subscription Sources

The tool comes with a default subscription, but you can add your own:

```bash
java -jar target/v2ray-tester-1.0.0-jar-with-dependencies.jar --add "https://example.com/v2ray-sub"
```

See [docs/more_sources.txt](docs/more_sources.txt) for a list of known subscription sources. A web search will also reveal many more, and a good starting point is the [v2ray-config topic on GitHub](https://github.com/topics/v2ray-config).

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
Usage: java -jar v2ray-tester-1.0.0-jar-with-dependencies.jar [options]

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
java -jar v2ray-tester-1.0.0-jar-with-dependencies.jar --add "https://example.com/v2ray-sub"

# List saved subscriptions
java -jar v2ray-tester-1.0.0-jar-with-dependencies.jar --list

# Remove a subscription
java -jar v2ray-tester-1.0.0-jar-with-dependencies.jar --remove "https://example.com/v2ray-sub"

# Test only previously saved servers (no network fetching)
java -jar v2ray-tester-1.0.0-jar-with-dependencies.jar --no-fetching

# Fetch configs only (skip testing)
java -jar v2ray-tester-1.0.0-jar-with-dependencies.jar --just-fetch


```

## How It Works

### Testing Pipeline

```
┌─────────────────────┐
│  Fetch Subscriptions│  Subscription URLs + freev2ray.cc
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Parse Server URLs  │  vmess://, vless://, trojan://, ss:// → ServerConfig objects
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
│  Phase 2: Download Speed Test   │  Xray core as SOCKS5 proxy → download 10 MB test file
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

The speed test works by running a local [Xray-core](https://github.com/XTLS/Xray-core) process with a generated config that routes traffic through the server under test. The tool then downloads a test file through the local SOCKS5 proxy and measures throughput.

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
│   │   ├── App.java                    # Main entry point, orchestration, CLI parsing
│   │   ├── CliArgs.java                # Command-line argument helpers
│   │   ├── FreeV2RayCcScraper.java     # Scraper for freev2ray.cc
│   │   ├── JarFolderTool.java          # Locates the running JAR's folder
│   │   ├── Parser.java                 # Parses vmess/vless/trojan/ss URLs
│   │   ├── QrHelper.java               # QR code generation (PNG + ASCII)
│   │   ├── ResultReporter.java         # Writes all.txt / best.txt output
│   │   ├── ServerConfig.java           # Immutable server config model (Builder pattern)
│   │   ├── ServerConfigWithTestResult.java  # Wraps config with ping/speed results
│   │   ├── SpeedTester.java            # Xray core download speed measurement
│   │   ├── SubscriptionManager.java    # Manages ~/.v2ray-subscriptions.txt
│   │   ├── Util.java                   # Shared utilities (atomicWrite, retryNetwork)
│   │   ├── XrayConfigBuilder.java      # Generates Xray JSON config from ServerConfig
│   │   └── XrayCoreManager.java        # Downloads, extracts, and caches the Xray binary
│   ├── main/resources/
│   │   └── version.properties          # Version number (injected by Maven)
│   └── test/java/free/svoss/tools/v2ray/
│       ├── AppCalcScoreTest.java       # Tests for composite score calculation
│       ├── AppIsPrivateHostTest.java   # Tests for private/internal host detection
│       ├── ParserTest.java             # Tests for URL parsing (vmess, vless, trojan, ss)
│       ├── ServerConfigBuilderValidationTest.java  # Builder validation tests
│       └── ServerConfigWithTestResultTest.java     # Round-trip serialization tests
├── pom.xml                             # Maven build config
├── multistart.bat                      # Cross-platform polyglot launcher (Windows + Linux)
├── start.bat                           # Windows launcher
├── start.sh                            # Linux/macOS launcher
├── rebuild.bat                         # Windows: rebuild the JAR
├── rebuildAndStart.bat                 # Windows: rebuild and run
├── packWithTimestamp.bat               # Windows: create a timestamped zip archive
└── packWithTimestamp.sh                # Linux/macOS: create a timestamped zip archive
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
- URL parsing for all four protocols (vmess, vless, trojan, shadowsocks)

## CI

GitHub Actions runs on pushes and PRs to `main`:

```yaml
# .github/workflows/ci.yml
runs-on: ubuntu-latest
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      java-version: '8'
      distribution: 'temurin'
  - run: mvn -B clean verify
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| [Gson](https://github.com/google/gson) | 2.10.1 | JSON serialization/deserialization |
| [Jsoup](https://jsoup.org/) | 1.22.1 | HTTP fetching and HTML parsing (subscriptions, freev2ray.cc) |
| [ZXing](https://github.com/zxing/zxing) | 3.5.3 | QR code generation (PNG + ASCII terminal rendering) |
| [JUnit 5](https://junit.org/junit5/) | 5.10.2 | Unit testing (test scope) |

## License

See the repository for license information.
