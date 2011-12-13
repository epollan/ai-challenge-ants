import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Author: evan.pollan
 * Date: 11/29/11
 * Time: 1:50 PM
 */
public abstract class LogFacade {

    protected abstract void implSetTestConfig();

    protected abstract void implSetProdConfig();

    protected abstract void error(String pattern, Throwable error, Object... args);

    protected abstract void info(String pattern, Object... args);

    protected abstract void debug(String pattern, Object... args);

    protected abstract boolean isDebugEnabled();

    private static final Map<Class, LogFacade> _loggers = new HashMap<Class, LogFacade>();

    public static LogFacade get(Class c) {
        LogFacade f = null;
        if (c != null) {
            f = _loggers.get(c);
        }
        if (f == null) {
            f = new JavaLogFacade(c);
            if (c != null) {
                _loggers.put(c, f);
            }
        }
        return f;
    }

    public static void setTestConfig() {
        get(null).implSetTestConfig();
    }

    public static void setProdConfig() {
        get(null).implSetProdConfig();
    }

    private static class JavaLogFacade extends LogFacade {
        private Logger _log;
        private String _sourceClass;

        private JavaLogFacade(Class c) {
            _sourceClass = (c != null) ? c.getName() : "";
            _log = Logger.getLogger(_sourceClass);
        }

        @Override
        protected void implSetTestConfig() {
            _log.setLevel(Level.FINE);
            _log.addHandler(new ConsoleHandler());
        }

        @Override
        protected void implSetProdConfig() {
            try {
                FileHandler fh = new FileHandler("oldnbusted.log", 10 * 1024 * 1024, 10, true);
                fh.setFormatter(new LogFormat());
                _log.addHandler(fh);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            _log.setLevel(Level.INFO);
        }

        @Override
        protected final void error(String pattern, Throwable error, Object... args) {
            if (_log.isLoggable(Level.SEVERE)) {
                _log.log(Level.SEVERE, String.format(pattern, args), error);
            }
        }

        @Override
        protected final void info(String pattern, Object... args) {
            if (_log.isLoggable(Level.INFO)) {
                _log.logp(Level.INFO, _sourceClass, "", String.format(pattern, args));
            }
        }

        @Override
        protected final void debug(String pattern, Object... args) {
            if (_log.isLoggable(Level.FINE)) {
                _log.logp(Level.FINE, _sourceClass, "", String.format(pattern, args));
            }
        }

        @Override
        protected final boolean isDebugEnabled() {
            return _log.isLoggable(Level.FINE);
        }

        private static class LogFormat extends Formatter {

            private Date _date = new Date();
            private SimpleDateFormat _format = new SimpleDateFormat("HH:mm:ss.S");

            // Line separator string.  This is the value of the line.separator
            // property at the moment that the SimpleFormatter was created.
            private String lineSeparator = String.format("%n");

            /**
             * Format the given LogRecord.
             *
             * @param record the log record to be formatted.
             * @return a formatted log record
             */
            public String format(LogRecord record) {
                StringBuffer sb = new StringBuffer();
                // Minimize memory allocations here.
                _date.setTime(record.getMillis());
                sb.append(record.getThreadID())
                  .append(" [")
                  .append(_format.format(_date))
                  .append('/')
                  .append(record.getSourceClassName() == null ?
                          record.getLoggerName() : record.getSourceClassName())
                  .append("] [")
                  .append(record.getLevel().getLocalizedName())
                  .append("] ")
                  .append(formatMessage(record))
                  .append(lineSeparator);
                if (record.getThrown() != null) {
                    try {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        record.getThrown().printStackTrace(pw);
                        pw.close();
                        sb.append(sw.toString());
                    } catch (Exception ex) {
                    }
                }
                return sb.toString();
            }
        }
    }
}
