package dev.jarcadia;

record TableConfig(String table, String[] ignoredCols, boolean watchInsert, boolean watchUpdate,
                          boolean watchDelete, boolean fireDmlEvents, String[] rtrColumns) {

    protected boolean fireRtrEvents() {
        return rtrColumns() != null && rtrColumns().length > 0;
    }
}

