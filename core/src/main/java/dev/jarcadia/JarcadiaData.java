package dev.jarcadia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class JarcadiaData {

    private static final int TARGET_SCHEMA_VERSION = 0;

    private static final Logger logger = LoggerFactory.getLogger(JarcadiaData.class);

    protected static JDConfig configure(DataSource dataSource, Class<? extends JDRepo> repoType) {
        return new JDConfig(dataSource, repoType);
    }

    protected static void apply(DataSource dataSource, List<TableConfig> target, Class<? extends JDRepo> repoType)
            throws JDLockedException, JDException {

        try (Connection conn = dataSource.getConnection()) {
            applyWithConnection(conn, target, repoType);
        } catch (SQLException ex) {
            throw new JDException(ex);
        }
    }

    private static void applyWithConnection(Connection conn, List<TableConfig> target, Class<? extends JDRepo> repoType)
            throws JDLockedException, SQLException, JDException {
        try {
            Constructor<? extends JDRepo> constructor = repoType.getConstructor(Connection.class);
            JDRepo repo = constructor.newInstance(conn);
            applyWithRepo(conn, repo, target);
        } catch (NoSuchMethodException | InstantiationException |
                IllegalAccessException | InvocationTargetException ex) {
            throw new JDException("Unable to construct repository of type " + repoType.getSimpleName());
        }
    }

    private static void applyWithRepo(Connection conn, JDRepo repo, List<TableConfig> target) throws SQLException,
            JDLockedException, JDException {

        if (!repo.tryLock()) {
            throw new JDLockedException("Unable to acquire lock");
        }

        try {
            logger.info("Acquired jarcadia data lock");
            applyWithLock(conn, repo, target);
        } finally {
            logger.info("Releasing jarcadia data lock");
            repo.releaseLock();
            logger.info("Released jarcadia data lock");
        }
    }

    private static void applyWithLock(Connection conn, JDRepo repo, List<TableConfig> target)
            throws SQLException, JDException {

        boolean savedAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            applyInTransaction(repo, target);
            conn.commit();
            logger.info("Committed");
        } catch (SQLException ex) {
            conn.rollback();
            logger.info("Rolled back Jarcadia data", ex);
            throw ex;
        } catch (Exception ex) {
            conn.rollback();
            logger.info("Rolled back Jarcadia data", ex);
            throw new JDException(ex);
        }
        finally {
            // Restore auto-commit
            conn.setAutoCommit(savedAutoCommit);
        }
    }

    private static void applyInTransaction(JDRepo repo, List<TableConfig> targetTableConfs)
            throws SQLException, IOException, JDException {

        SqlScriptGenerator sqlScriptGenerator = new SqlScriptGenerator();

        int startingSchemaVersion = repo.isJarcadiaDataInstalled() ?
                repo.getJarcadiaDataSchemaVersion() : -1;

        for (int schemaVersion=startingSchemaVersion+1; schemaVersion<=TARGET_SCHEMA_VERSION; schemaVersion++) {
            logger.info("Migrating to schema version {}", schemaVersion);
            String migrationScript = sqlScriptGenerator.generateMigrationScript(schemaVersion);
            repo.runScript(migrationScript);
        }

        List<TableConfig> currentTableConfs = repo.getTableConfigs();
        logger.info("Current {}", currentTableConfs);
        logger.info("Target {}", targetTableConfs);

        List<Action> actions = ConfigChangeDetector.detectChanges(currentTableConfs, targetTableConfs);
        logger.info("Actions {}", actions);

        if (actions.size() > 0) {

            Set<String> actionTables = actions.stream()
                    .filter(action -> action.targetTable() != null)
                    .map(action -> action.targetTable())
                    .collect(Collectors.toSet());

            for (TableConfig targetConf : targetTableConfs) {

                String table = targetConf.table();
                logger.info("Loading metadata for table {}", table);

                if (!actionTables.contains(table)) {
                    continue;
                }

                List<String> pks = repo.getPrimaryKeys(table);
                if (pks.size() > 1) {
                    throw new SQLException("Not supported");
                }

                List<Column> columns = repo.getColumns(table);
                Column pk = columns.stream()
                        .filter(c -> c.name().equals(pks.get(0)))
                        .findAny().get();

                columns.remove(pk);

                sqlScriptGenerator.prepareContext(table, targetConf, pk, columns);
            }

            for (Action action : actions) {
                String actionScript = sqlScriptGenerator.generateActionScript(action);
                repo.runScript(actionScript);
            }
        }
    }
}
