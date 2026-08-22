package free.svoss.tools.v2ray;



import java.lang.management.ManagementFactory;
import java.util.List;

public final class Ansi {
    public final static String LIGHT_SHADE = "░";
    public final static String MEDIUM_SHADE = "▒";
    public final static String DARK_SHADE = "▓";
    public final static String FULL_BLOCK = "█";
    public final static char[] CHECK_MARKS = "✓✔✅☑".toCharArray();
    public final static char[] CROSSES = "❌⨯˟❎✚".toCharArray();
    public final static char[] CHESS_OUTLINE = "♔♕♖♗♘♙".toCharArray();
    public final static char[] CHESS_FILLED = "♚♛♜♝♞♟".toCharArray();
    public final static String BLACK_FG = "\033[30m";
    public final static String BLACK_BG = "\033[40m";
    public final static String DARK_RED_FG = "\033[31m";
    public final static String DARK_RED_BG = "\033[41m";
    public final static String DARK_GREEN_FG = "\033[32m";
    public final static String DARK_GREEN_BG = "\033[42m";
    public final static String DARK_YELLOW_FG = "\033[33m";
    public final static String DARK_YELLOW_BG = "\033[43m";
    public final static String DARK_BLUE_FG = "\033[34m";
    public final static String DARK_BLUE_BG = "\033[44m";
    public final static String DARK_MAGENTA_FG = "\033[35m";
    public final static String DARK_MAGENTA_BG = "\033[45m";
    public final static String DARK_CYAN_FG = "\033[36m";
    public final static String DARK_CYAN_BG = "\033[46m";
    public final static String LIGHT_GRAY_FG = "\033[37m";
    public final static String LIGHT_GRAY_BG = "\033[47m";
    public final static String DARK_GRAY_FG = "\033[90m";
    public final static String DARK_GRAY_BG = "\033[100m";
    public final static String LIGHT_RED_FG = "\033[91m";
    public final static String LIGHT_RED_BG = "\033[101m";
    public final static String LIGHT_GREEN_FG = "\033[92m";
    public final static String LIGHT_GREEN_BG = "\033[102m";
    public final static String LIGHT_YELLOW_FG = "\033[93m";
    public final static String LIGHT_YELLOW_BG = "\033[103m";
    public final static String LIGHT_BLUE_FG = "\033[94m";
    public final static String LIGHT_BLUE_BG = "\033[104m";
    public final static String LIGHT_MAGENTA_FG = "\033[95m";
    public final static String LIGHT_MAGENTA_BG = "\033[105m";
    public final static String LIGHT_CYAN_FG = "\033[96m";
    public final static String LIGHT_CYAN_BG = "\033[106m";
    public final static String WHITE_FG = "\033[97m";
    public final static String WHITE_BG = "\033[107m";
    public final static String BOLD = "\033[1m";
    public final static String UNDERLINED = "\033[4m";
    public final static String NOT_UNDERLINED = "\033[24m";
    public final static String REVERSED_TEXT = "\033[7m";
    public final static String POSITIVE_TEXT = "\033[27m";
    public final static String RESET = "\033[0m";

    public static final String CLS="\033[2J";
    public static final String NO_BLINKING="\033[?25l\033[?12l";
    public static final String UNICODE_RESET = "\u001B[0m";
    public static final String UNICODE_NBSP = "\u00A0";

}


