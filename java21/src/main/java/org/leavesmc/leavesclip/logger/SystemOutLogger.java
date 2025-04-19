package org.leavesmc.leavesclip.logger;

import org.spongepowered.asm.logging.Level;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class SystemOutLogger extends Logger {
    private final String id;
    private final String type;
    private final PrintStream out;
    private final PrintStream err;
    private final Level logLevel = Level.INFO; // TODO: Make this configurable
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public SystemOutLogger(String id) {
        this(id, "SystemOutLogger");
    }

    public SystemOutLogger(String id, String type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.out = System.out;
        this.err = System.err;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return type;
    }

    private boolean isLevelEnabled(Level level) {
        return level != null && level.ordinal() <= this.logLevel.ordinal();
    }

    private static String formatPlaceholders(String message, Object... params) {
        if (message == null || params == null || params.length == 0) return message;
        StringBuilder sb = new StringBuilder();
        int paramIdx = 0, lastIdx = 0;
        for (int i = 0; i < message.length(); i++) {
            if (i + 1 < message.length() && message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                sb.append(message, lastIdx, i);
                if (paramIdx < params.length) {
                    sb.append(params[paramIdx++]);
                } else {
                    sb.append("{}");
                }
                i++; // skip '}'
                lastIdx = i + 1;
            }
        }
        if (lastIdx < message.length()) {
            sb.append(message.substring(lastIdx));
        }
        if (paramIdx < params.length) {
            sb.append(" [");
            for (int i = paramIdx; i < params.length; i++) {
                if (i > paramIdx) sb.append(", ");
                sb.append(params[i]);
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private String prefix(Level level) {
        return "[" + timeFormat.format(new Date()) + " " + level.name() + "]: [" + id + "] ";
    }

    @Override
    public void catching(Level level, Throwable t) {
        if (!isLevelEnabled(level)) return;
        PrintStream stream = (level == Level.ERROR || level == Level.FATAL) ? err : out;
        stream.println(prefix(level) + "Exception caught:");
        t.printStackTrace(stream);
    }

    @Override
    public void catching(Throwable t) {
        catching(Level.ERROR, t);
    }

    @Override
    public void debug(String message, Object... params) {
        if (!isLevelEnabled(Level.DEBUG)) return;
        out.println(prefix(Level.DEBUG) + formatPlaceholders(message, params));
    }

    @Override
    public void debug(String message, Throwable t) {
        if (!isLevelEnabled(Level.DEBUG)) return;
        out.println(prefix(Level.DEBUG) + message);
        t.printStackTrace(out);
    }

    public void error(Throwable t, String message, Object... params) {
        if (!isLevelEnabled(Level.ERROR)) return;
        err.println(prefix(Level.ERROR) + formatPlaceholders(message, params));
        t.printStackTrace(err);
    }

    @Override
    public void error(String message, Object... params) {
        if (!isLevelEnabled(Level.ERROR)) return;
        err.println(prefix(Level.ERROR) + formatPlaceholders(message, params));
    }

    @Override
    public void error(String message, Throwable t) {
        if (!isLevelEnabled(Level.ERROR)) return;
        err.println(prefix(Level.ERROR) + message);
        t.printStackTrace(err);
    }

    @Override
    public void fatal(String message, Object... params) {
        if (!isLevelEnabled(Level.FATAL)) return;
        err.println(prefix(Level.FATAL) + formatPlaceholders(message, params));
    }

    @Override
    public void fatal(String message, Throwable t) {
        if (!isLevelEnabled(Level.FATAL)) return;
        err.println(prefix(Level.FATAL) + message);
        t.printStackTrace(err);
    }

    @Override
    public void info(String message, Object... params) {
        if (!isLevelEnabled(Level.INFO)) return;
        out.println(prefix(Level.INFO) + formatPlaceholders(message, params));
    }

    @Override
    public void info(String message, Throwable t) {
        if (!isLevelEnabled(Level.INFO)) return;
        out.println(prefix(Level.INFO) + message);
        t.printStackTrace(out);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        if (!isLevelEnabled(level)) return;
        PrintStream stream = (level == Level.ERROR || level == Level.FATAL) ? err : out;
        stream.println(prefix(level) + formatPlaceholders(message, params));
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        if (!isLevelEnabled(level)) return;
        PrintStream stream = (level == Level.ERROR || level == Level.FATAL) ? err : out;
        stream.println(prefix(level) + message);
        t.printStackTrace(stream);
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        if (isLevelEnabled(Level.ERROR)) {
            err.println(prefix(Level.ERROR) + "Throwing exception: " + t.getClass().getName());
            t.printStackTrace(err);
        }
        return t;
    }

    @Override
    public void trace(String message, Object... params) {
        if (!isLevelEnabled(Level.TRACE)) return;
        out.println(prefix(Level.TRACE) + formatPlaceholders(message, params));
    }

    @Override
    public void trace(String message, Throwable t) {
        if (!isLevelEnabled(Level.TRACE)) return;
        out.println(prefix(Level.TRACE) + message);
        t.printStackTrace(out);
    }

    public void warn(Throwable t, String message, Object... params) {
        if (!isLevelEnabled(Level.WARN)) return;
        err.println(prefix(Level.WARN) + formatPlaceholders(message, params));
        t.printStackTrace(err);
    }

    @Override
    public void warn(String message, Object... params) {
        if (!isLevelEnabled(Level.WARN)) return;
        out.println(prefix(Level.WARN) + formatPlaceholders(message, params));
    }

    @Override
    public void warn(String message, Throwable t) {
        if (!isLevelEnabled(Level.WARN)) return;
        out.println(prefix(Level.WARN) + message);
        t.printStackTrace(out);
    }
}
