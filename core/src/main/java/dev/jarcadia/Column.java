package dev.jarcadia;

import java.util.Map;

record Column(String name, String type, boolean nullable) {

    protected Map<String, Object> toMap() {
        return Map.of("name", name, "type", type, "nullable", nullable);
    }
}
