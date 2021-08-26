package dev.jarcadia;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JDConfig {

    private final DataSource dataSource;
    private final Class<? extends JDRepo> repoType;
    private final List<TableConfig> tableConfigList;

    public JDConfig(DataSource dataSource, Class<? extends JDRepo> repoType) {
        this.dataSource = dataSource;
        this.repoType = repoType;
        this.tableConfigList = new ArrayList<>();
    }

    public JDConfig target(String table, Consumer<JDTableConfig> conf) {
        JDTableConfig jdtc = new JDTableConfig(table);
        conf.accept(jdtc);
        tableConfigList.add(jdtc.buildTableConfig());
        return this;
    }

    public void apply() throws JDLockedException, JDException {
        JarcadiaData.apply(dataSource, tableConfigList, repoType);
    }
}
