package dev.jarcadia;

import javax.sql.DataSource;

public class JDPostgres {
    public static JDPostgresConfig configure(DataSource dataSource, Retask retask) {
        return new JDPostgresConfig(JarcadiaData.configure(dataSource, JDRepoPostgres.class), dataSource, retask);
    }
}
