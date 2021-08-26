package dev.jarcadia;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.SQLException;
import java.util.List;

public class TestMigration {

    @Test
    public void test() throws SQLException, JDException, JDLockedException {

        PGSimpleDataSource ds = new PGSimpleDataSource() ;
        ds.setServerNames(new String[]{"localhost"});
        ds.setDatabaseName( "watchdog" );
        ds.setUser("watchdog");
        ds.setPassword("watchdog");

        TableConfig target = new TableConfig("emp", new String[0], false,  true,
                false, true, new String[0]);

        JarcadiaData.apply(ds, List.of(target), JDRepoPostgres.class);
    }
}
