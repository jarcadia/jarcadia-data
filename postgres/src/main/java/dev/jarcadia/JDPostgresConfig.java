package dev.jarcadia;

import com.impossibl.postgres.jdbc.PGDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

public class JDPostgresConfig {

    private final JDConfig baseConfig;
    private final DataSource dataSource;
    private final Retask retask;

    protected JDPostgresConfig(JDConfig baseConfig, DataSource dataSource, Retask retask) {
        this.baseConfig = baseConfig;
        this.dataSource = dataSource;
        this.retask = retask;
    }

    public JDPostgresConfig target(String table, Consumer<JDTableConfig> conf) {
        baseConfig.target(table, conf);
        return this;
    }

    public JDPostgresListener applyAndCreateListener() throws JDLockedException, JDException {
        baseConfig.apply();
        PGDataSource listeningDataSource = createListeningDataSource();
        return new JDPostgresListener(retask, listeningDataSource, dataSource);
    }

    private PGDataSource createListeningDataSource() throws JDException {
        return switch (dataSource.getClass().getName()) {
            case "com.zaxxer.hikari.HikariDataSource",
                    "io.micronaut.configuration.jdbc.hikari.HikariUrlDataSource" -> createFromHikariDataSource();
            default -> throw new JDException("Unable to initialize using " +
                    dataSource.getClass().getName());
        };
    }

    private PGDataSource createFromHikariDataSource() throws JDException {
        try {
            PGDataSource ds = new PGDataSource();
            String url = (String) dataSource.getClass().getMethod("getUrl").invoke(dataSource);
            ds.setDatabaseUrl(url.replace("jdbc:postgresql:", "jdbc:pgsql:"));
            ds.setUser((String) dataSource.getClass().getMethod("getUsername").invoke(dataSource));
            ds.setPassword( (String) dataSource.getClass().getMethod("getPassword").invoke(dataSource));
            return ds;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new JDException("Unable to create listening data source", ex);
        }
    }
}