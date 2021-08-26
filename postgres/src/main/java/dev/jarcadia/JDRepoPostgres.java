package dev.jarcadia;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class JDRepoPostgres extends JDRepo {

    public JDRepoPostgres(Connection conn) {
        super(conn);
    }

    @Override
    public boolean tryLock() throws SQLException {
        return queryForObj("select pg_try_advisory_lock(1)", rs -> rs.getBoolean(1));
    }

    @Override
    public boolean releaseLock() throws SQLException {
        return queryForObj("select pg_advisory_unlock(1)", rs -> rs.getBoolean(1));
    }

    private final String IS_INSTALLED_QUERY = """
    SELECT EXISTS (
        SELECT FROM pg_catalog.pg_class c
        WHERE  c.relname = 'jd_config'
        AND    c.relkind = 'r'
    )""";
    @Override
    protected boolean isJarcadiaDataInstalled() throws SQLException {
        return queryForObj(IS_INSTALLED_QUERY, rs -> rs.getBoolean(1));
    }

    private final String COLUMN_QUERY = """
        SELECT attname, format_type(atttypid, atttypmod), NOT attnotnull
        FROM pg_attribute
        WHERE attrelid = ?::regclass AND attnum > 0 AND NOT attisdropped
        ORDER BY attnum
    """;

    @Override
    protected List<Column> getColumns(String table) throws SQLException {
        return queryForList(COLUMN_QUERY, List.of(table),
                rs -> new Column(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
    }

    private final String PK_QUERY = """
        SELECT a.attname
        FROM   pg_index i
        JOIN   pg_attribute a ON a.attrelid = i.indrelid
                             AND a.attnum = ANY(i.indkey)
        WHERE  i.indrelid = ?::regclass
        AND    i.indisprimary
    """;
    @Override
    protected List<String> getPrimaryKeys(String table) throws SQLException {
        return queryForList(PK_QUERY, List.of(table), rs -> rs.getString(1));
    }
}
