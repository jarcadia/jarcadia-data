package dev.jarcadia;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import com.impossibl.postgres.jdbc.PGDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JDPostgresListener implements Closeable, PGNotificationListener {

    private final Logger logger = LoggerFactory.getLogger(JDPostgresListener.class);

    private final Retask retask;
    private final PGDataSource listeningDataSource;
    private final DataSource dataSource;

    private final Phaser active;
    private PGConnection listenConn;

    protected JDPostgresListener(Retask retask, PGDataSource listeningDataSource, DataSource dataSource) {
        this.retask = retask;
        this.listeningDataSource = listeningDataSource;
        this.dataSource = dataSource;
        this.active = new Phaser();
    }

    //    https://stackoverflow.com/questions/21632243/how-do-i-get-asynchronous-event-driven-listen-notify-support-in-java-using-a-p
    public void start() throws JDException {

        /* Register an initial party to force the Phaser to be active (non-zero) until fully shut down */
        active.register();

        try {
            listenConn = (PGConnection) listeningDataSource.getConnection();
        } catch (SQLException ex) {
            active.arriveAndDeregister();
            throw new RuntimeException("Unable to connect to postgres", ex);
        }

        logger.info("Starting jarcadia postgres listener for {}", listeningDataSource.getUrl());
        listenConn.addNotificationListener(this);

        try {
            Statement stmt = listenConn.createStatement();
            stmt.execute("LISTEN jd_channel");
            stmt.close();
        } catch (Throwable ex) {
            active.arriveAndDeregister();
            logger.warn("Received an error from LISTEN, is it a close?");
//            throw new JarcadiaException("Unable to listen to postgres notifications", ex);
        }

        // Register party while processing backlog
        active.register();

        try (Connection conn = dataSource.getConnection();
            Statement query = conn.createStatement()) {
            ResultSet rs = query.executeQuery("select * from jd_dml_event order by event_id asc");
            while (rs.next()) {
                processDmlEvent(rs.getLong("event_id"), rs.getString("stmt"),
                        rs.getString("tbl"), rs.getString("data"));
            }
        } catch (Exception ex) {
            throw new JDException("Unable to process DML Event backlog", ex);
        } finally {
            active.arriveAndDeregister();
        }

    }

    @Override
    public void notification(int processId, String channelName, String payload) {
        logger.info("Received {}", payload);

        int idx1 = payload.indexOf(":");
        int idx2 = payload.indexOf(":", idx1+1);
        int idx3 = payload.indexOf(":", idx2+1);

        processDmlEvent(Long.parseLong(payload.substring(0, idx1)), payload.substring(idx1+1, idx2),
                payload.substring(idx2+1, idx3), payload.substring(idx3+1));
    }

    private void processDmlEvent(long eventId, String statement, String table, String data) {
        if (retask.submitDmlEvent(eventId, statement, table, data)) {
            try(Connection conn = dataSource.getConnection();
                PreparedStatement deleteStmt = conn.prepareStatement("delete from jd_dml_event where event_id = ?")
            ) {
                deleteStmt.setLong(1, eventId);
                int rows = deleteStmt.executeUpdate();
                if (rows == 0) {
                    logger.warn("Delete did not remove dml_event {}", eventId);
                }
            } catch (SQLException ex) {
                logger.warn("Unable to remove dml_event {}", eventId, ex);
            }
        }


    }


    @Override
    public void close() {
        if (listenConn == null) {
            active.arriveAndDeregister();
        } else {
            try {
                this.listenConn.close();
            } catch (SQLException ex) {
                logger.warn("Exception while closing connection", ex);
            } finally {
                active.arriveAndDeregister();
            }
        }
    }

    protected void awaitDrainComplete() {
        try {
            this.active.awaitAdvanceInterruptibly(1);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for item processing to complete");
        }
    }

    protected void awaitDrainComplete(long timeout, TimeUnit unit) throws TimeoutException {
        try {
            this.active.awaitAdvanceInterruptibly(1, timeout, unit);
        } catch(TimeoutException ex){
            logger.error("Timeout while waiting for item processing to complete");
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for item processing to complete");
        }
    }
}
