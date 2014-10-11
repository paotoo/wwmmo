package au.com.codeka.warworlds.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.ErrorReport;
import au.com.codeka.warworlds.server.ctrl.ErrorReportsController;

/**
 * This is an implementation of {@see Handler} which records the last few lines of logging and
 * then writes errors to the database.
 */
public class ErrorReportingLoggingHandler extends Handler {
    private ArrayList<String> mLogBuffer;
    private SimpleFormatter mSimpleFormatter;

    public ErrorReportingLoggingHandler() {
        mLogBuffer = new ArrayList<String>();
        mSimpleFormatter = new SimpleFormatter();
    }

    /**
     * This is called when the server starts, we need to install ourselves in the logger stream.
     */
    public static void setup() {
        Logger.getGlobal().addHandler(new ErrorReportingLoggingHandler());
    }

    /**
     * This is called when a log record needs to actually published. If the record is error level or
     * above, we'll write our buffer of logs to the database. Otherwise, we'll just append this to
     * the buffer.
     */
    @Override
    public void publish(LogRecord record) {
        // ignore anything below 'info' level...
        if (record.getLevel() == Level.FINE || record.getLevel() == Level.FINER || record.getLevel() == Level.FINEST) {
            return;
        }

        mLogBuffer.add(mSimpleFormatter.format(record));
        if (mLogBuffer.size() > 10) {
            mLogBuffer.remove(0);
        }

        if (record.getLevel() == Level.SEVERE) {
            saveErrorReport(record);
        }
    }

    private void saveErrorReport(LogRecord record) {
        try {
            Runtime rt = Runtime.getRuntime();

            StringBuilder sb = new StringBuilder();
            for (String line : mLogBuffer) {
                sb.append(line);
                sb.append("\r\n");
            }

            ErrorReport.Builder builder = new ErrorReport.Builder()
                .context(RequestContext.i.getContextName())
                .heap_size(rt.totalMemory())
                .heap_free(rt.freeMemory())
                .heap_allocated(rt.totalMemory() - rt.freeMemory())
                .log_output(sb.toString())
                .report_time(DateTime.now().getMillis())
                .server_request_user_agent(RequestContext.i.getUserAgent())
                .server_request_qs(RequestContext.i.getQueryString());

            if (record.getThrown() != null) {
                Throwable thrown = record.getThrown();

                Throwable innermostThrowable = thrown;
                while (innermostThrowable.getCause() != null) {
                    innermostThrowable = innermostThrowable.getCause();
                }

                StringWriter stacktrace = new StringWriter();
                thrown.printStackTrace(new PrintWriter(stacktrace));

                builder.exception_class(innermostThrowable.getClass().getName())
                       .message(thrown.getMessage())
                        .stack_trace(stacktrace.toString());
            }

            new ErrorReportsController().saveErrorReport(builder.build());
        } catch (Exception e) {
            // this is probably bad, but we'll just ignore them...
        }
    }

    @Override
    public void close() throws SecurityException {
    }

    @Override
    public void flush() {
    }
}
