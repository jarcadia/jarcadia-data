package dev.jarcadia;

public class JDTableConfig {

    private final String table;

    private String[] ignore;
    private boolean watchUpdates;
    private boolean watchInserts;
    private boolean watchDeletes;
    private boolean fireDmlEvents;
    private String[] rtrColumns;

    protected JDTableConfig(String table) {
        this.table = table;
        this.ignore = new String[0];
        this.rtrColumns = new String[0];
    }

    public JDTableConfig ignore(String... columns) {
        this.ignore = columns;
        return this;
    }

    public JDTableConfig watchInserts() {
        watchInserts = true;
        return this;
    }

    public JDTableConfig watchUpdates() {
        watchUpdates = true;
        return this;
    }

    public JDTableConfig watchDeletes() {
        watchDeletes = true;
        return this;
    }

    public JDTableConfig fireDmlEvents() {
        fireDmlEvents = true;
        return this;
    }

    public JDTableConfig setRealTimeRoomColumns(String... columns) {
        rtrColumns = columns;
        return this;
    }

    protected TableConfig buildTableConfig() {
        return new TableConfig(table, ignore, watchInserts, watchUpdates, watchDeletes, fireDmlEvents, rtrColumns);
    }
}
