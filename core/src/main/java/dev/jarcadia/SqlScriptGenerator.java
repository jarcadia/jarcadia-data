package dev.jarcadia;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class SqlScriptGenerator {

    private final VelocityEngine velocity;
    private final Map<String, VelocityContext> contextMap;
    private final VelocityContext emptyContext;

    protected SqlScriptGenerator() {
        this.velocity = createEngine();
        this.contextMap = new HashMap<>();
        this.emptyContext = new VelocityContext();
    }

    private VelocityEngine createEngine() {
        VelocityEngine velocity = new VelocityEngine();
        velocity.setProperty("runtime.references.strict", "true");
        velocity.setProperty("resource.loader", "class");
        velocity.setProperty("class.resource.loader.description", "Velocity Classpath Resource Loader");
        velocity.setProperty("class.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocity.init();
        return velocity;
    }

    protected String generateMigrationScript(int schemaVersion) throws IOException {
        String template = String.format("templates/migrations/%03d.sql.vm", schemaVersion);

        try (StringWriter writer = new StringWriter()) {
            velocity.mergeTemplate(template, "UTF-8", emptyContext, writer);
            writer.flush();
            return writer.toString();
        }
    }

    protected void prepareContext(String table, TableConfig target, Column pk, List<Column> columns) {
        contextMap.computeIfAbsent(table, t -> {
            VelocityContext ctx = new VelocityContext();
            ctx.put("table", table);
            ctx.put("ignoredCols", target.ignoredCols());
            ctx.put("csIgnoredCols", target.ignoredCols() == null ? "" :
                    String.join(",", target.ignoredCols()));
            ctx.put("watchInsert", target.watchInsert());
            ctx.put("watchUpdate", target.watchUpdate());
            ctx.put("watchDelete", target.watchDelete());
            ctx.put("fireDmlEvents", target.fireDmlEvents());
            ctx.put("fireRtrEvents", target.fireRtrEvents());
            ctx.put("rtrColumns", target.rtrColumns());
            ctx.put("csRtrCols", target.rtrColumns() == null ? "" :
                    String.join(",", target.rtrColumns()));
            ctx.put("pk", pk.toMap());
            ctx.put("columns", columns.stream()
                    .map(col -> col.toMap())
                    .collect(Collectors.toList()));
            ctx.put("String", String.class);
            return ctx;
        });
    }

    protected String generateActionScript(Action action) throws IOException {

        String template = String.format("templates/actions/%s.sql.vm", toKebabCase(action.type()));

        VelocityContext ctx = action.targetTable() == null ? emptyContext : contextMap.get(action.targetTable());

        try (StringWriter writer = new StringWriter()) {
            velocity.mergeTemplate(template, "UTF-8", ctx, writer);
            writer.flush();
            return writer.toString();
        }
    }

    private static String toKebabCase(ActionType type) {
        StringBuilder builder = new StringBuilder();
        char[] chars = type.name().toCharArray();
        char c;
        for (int i=0; i<chars.length; i++) {
            c = chars[i];
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append("-");
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }


}
