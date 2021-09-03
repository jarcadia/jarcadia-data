package dev.jarcadia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

abstract class JDRepo {

    private final Logger logger = LoggerFactory.getLogger(JDRepo.class);

    private final Connection conn;

    private JDRepo() { conn = null; } /* prevent overriding default constructor */

    protected JDRepo(Connection conn) {
        this.conn = conn;
    }

    protected abstract boolean tryLock() throws SQLException;
    protected abstract boolean releaseLock() throws SQLException;
    protected abstract boolean isJarcadiaDataInstalled() throws SQLException;
    protected abstract List<Column> getColumns(String table) throws SQLException;
    protected abstract List<String> getPrimaryKeys(String table) throws SQLException;

    protected int getJarcadiaDataSchemaVersion() throws SQLException {
        return queryForObj("select schema_version from jd_config", rs -> rs.getInt(1));
    }

    protected List<TableConfig> getTableConfigs() throws SQLException {
        return queryForList("select * from jd_table_config", rs -> {

            String table = rs.getString("table_name");
            String[] ignoredCols = (String[]) rs.getArray("ignored_cols").getArray();
            boolean watchInsert = rs.getBoolean("watch_insert");
            boolean watchUpdate = rs.getBoolean("watch_update");
            boolean watchDelete = rs.getBoolean("watch_delete");
            boolean firesDmlEvents = rs.getBoolean("fire_dml_events");
            String[] rtrCols = (String[]) rs.getArray("rtr_cols").getArray();

            return new TableConfig(table.toLowerCase(), ignoredCols, watchInsert, watchUpdate, watchDelete, firesDmlEvents, rtrCols);
        });
    }

    protected void runScript(String script) throws SQLException {
        Iterable<String> stmts = new SqlScriptSplitter(script);

        for (String stmt : stmts) {
            logger.info("Running: {}", stmt);
            try(Statement sqlStmt = conn.createStatement()) {
                sqlStmt.executeUpdate(stmt);
            }
        }
    }

    protected <T> T queryForObj(String query, ResultSetHandler<T> resultSetHandler) throws SQLException {
        try(Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            rs.next();
            T result = resultSetHandler.handle(rs);
            if (rs.next()) {
                throw new SQLException("Expected single row result");
            }
            return result;
        }
    }

    protected <T> T queryForObj(String query, List<Object> params, ResultSetHandler<T> resultSetHandler) throws SQLException {
        try(PreparedStatement stmt = conn.prepareStatement(query)) {
            applyParams(stmt, params);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            T result = resultSetHandler.handle(rs);
            if (rs.next()) {
                throw new SQLException("Expected single row result");
            }
            return result;
        }
    }

    protected <T> List<T> queryForList(String query, RowMapper<T> rowMapper) throws SQLException {
        try(Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rowMapper.map(rs));
            }
            return result;
        }
    }

    protected <T> List<T> queryForList(String query, List<Object> params, RowMapper<T> rowMapper) throws SQLException {
        try(PreparedStatement stmt = conn.prepareStatement(query)) {
            applyParams(stmt, params);
            List<T> result = new ArrayList<>();
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(rowMapper.map(rs));
            }
            return result;
        }
    }

    protected int executeStatement(String stmt) throws SQLException {
        try(Statement sqlStmt = conn.createStatement()) {
            return sqlStmt.executeUpdate(stmt);
        }
    }

    private void applyParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i=0; i<params.size(); i++) {
            stmt.setObject(i+1, params.get(i));
        }
    }

    @FunctionalInterface
    protected interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    protected interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
