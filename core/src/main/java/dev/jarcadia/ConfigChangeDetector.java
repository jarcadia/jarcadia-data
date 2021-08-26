package dev.jarcadia;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class ConfigChangeDetector {

    protected static List<Action> detectChanges(List<TableConfig> currentConfigs, List<TableConfig> targetConfigs) throws
            JDException {


        Set<String> currentTables = currentConfigs.stream().map(TableConfig::table).collect(Collectors.toSet());
        Set<String> targetedTables = targetConfigs.stream().map(TableConfig::table).collect(Collectors.toSet());

        Set<String> removedTables = diff(currentTables, targetedTables);
        for (String removedTable : removedTables) {
            targetConfigs.add(empty(removedTable));
        }

        Set<String> addedTables = diff(targetedTables, currentTables);
        for (String addedTable : addedTables) {
            currentConfigs.add(empty(addedTable));
        }

        Set<Action> actionSet = new HashSet<>();

        if (hasAnyFireDmlEvents(currentConfigs) != hasAnyFireDmlEvents(targetConfigs)) {
            if (hasAnyFireDmlEvents(targetConfigs)) {
                actionSet.add(new Action(ActionType.InstallDmlEvents, null));
            } else {
                actionSet.add(new Action(ActionType.UninstallDmlEvents, null));
            }
        }

        if (hasAnyFireRtrEvents(currentConfigs) != hasAnyFireRtrEvents(targetConfigs)) {
            if (hasAnyFireRtrEvents(targetConfigs)) {
                actionSet.add(new Action(ActionType.InstallRealTimeRooms, null));
            } else {
                actionSet.add(new Action(ActionType.UninstallRealTimeRooms, null));
            }
        }

        Set<String> tables = new HashSet<>();
        tables.addAll(currentTables);
        tables.addAll(targetedTables);

        for (String table : tables) {
            TableConfig current = find(currentConfigs, table);
            TableConfig target = find(targetConfigs, table);

            Consumer<ActionType> actions = type -> actionSet.add(new Action(type, target.table()));

            boolean updated = diffTableConfigs(current, target, actions);

            if (addedTables.contains(table)) {
                actions.accept(ActionType.InsertTableConfigRow);
            } else if (removedTables.contains(table)) {
                actions.accept(ActionType.DeleteTableConfigRow);
            } else if (updated) {
                actions.accept(ActionType.UpdateTableConfigRow);
            }
        }

        Comparator<Action> c1 = Comparator.comparing(a -> a.type().ordinal());
        Comparator<Action> c2 = c1.thenComparing(a -> a.targetTable() == null ? "" : a.targetTable());
        return actionSet.stream()
                .sorted(c2)
                .collect(Collectors.toList());
    }

    private static boolean hasAnyFireDmlEvents(List<TableConfig> configs) {
        return configs.stream()
                .filter(TableConfig::fireDmlEvents)
                .findAny().isPresent();
    }

    private static boolean hasAnyFireRtrEvents(List<TableConfig> configs) {
        return configs.stream()
                .filter(TableConfig::fireRtrEvents)
                .findAny().isPresent();
    }

    private static boolean diffTableConfigs(TableConfig current, TableConfig target, Consumer<ActionType> actions) {
        boolean changed = false;
        if (current.fireDmlEvents() != target.fireDmlEvents()) {
            // If enabling/disabling dml events, trigger functions must be updated to add/remove dml event creation
            if (target.watchInsert()) {
                actions.accept(ActionType.CreateOrReplaceInsertTriggerFunc);
            }

            if (target.watchUpdate()) {
                actions.accept(ActionType.CreateOrReplaceUpdateTriggerFunc);
                actions.accept(ActionType.CreateOrReplaceUpdateTriggerDiffFunc);
            }

            if (target.watchDelete()) {
                actions.accept(ActionType.CreateOrReplaceDeleteTriggerFunc);
            }

            changed = true;
        }

        if (current.fireRtrEvents() != target.fireRtrEvents()) {

            if (target.fireRtrEvents()) {
                actions.accept(ActionType.CreateRealTimeRoomLinkTable);
            } else {
                actions.accept(ActionType.DropRealTimeRoomLinkTable);
                actions.accept(ActionType.DeleteRealTimeRoomsForTable);
            }

            if (target.watchInsert()) {
                actions.accept(ActionType.CreateOrReplaceInsertTriggerFunc);
            }

            if (target.watchUpdate()) {
                actions.accept(ActionType.CreateOrReplaceUpdateTriggerFunc);
                actions.accept(ActionType.CreateOrReplaceUpdateTriggerDiffFunc);
            }

            if (target.watchDelete()) {
                actions.accept(ActionType.CreateOrReplaceDeleteTriggerFunc);
            }

            changed = true;
        }

        if (current.watchInsert() != target.watchInsert()) {
            if (target.watchInsert()) {
                actions.accept(ActionType.CreateInsertTrigger);
                actions.accept(ActionType.CreateOrReplaceInsertTriggerFunc);
            } else {
                actions.accept(ActionType.DropInsertTrigger);
                actions.accept(ActionType.DropInsertTriggerFunc);
            }
            changed = true;
        }

        if (current.watchUpdate() != target.watchUpdate()) {
            if (target.watchUpdate()) {
                actions.accept(ActionType.CreateUpdateTrigger);
                actions.accept(ActionType.CreateOrReplaceUpdateTriggerFunc);
                actions.accept(ActionType.CreateOrReplaceUpdateTriggerDiffFunc);
            } else {
                actions.accept(ActionType.DropUpdateTrigger);
                actions.accept(ActionType.DropUpdateTriggerFunc);
                actions.accept(ActionType.DropUpdateTriggerDiffFunc);

            }
            changed = true;
        }

        if (current.watchDelete() != target.watchDelete()) {
            if (target.watchDelete()) {
                actions.accept(ActionType.CreateDeleteTrigger);
                actions.accept(ActionType.CreateOrReplaceDeleteTriggerFunc);
            } else {
                actions.accept(ActionType.DropDeleteTrigger);
                actions.accept(ActionType.DropDeleteTriggerFunc);
            }
            changed = true;
        }

        if (!Arrays.equals(current.ignoredCols(), target.ignoredCols())) {
            actions.accept(ActionType.CreateOrReplaceUpdateTriggerDiffFunc);
            changed = true;
        }

        if (!Arrays.equals(current.rtrColumns(), target.rtrColumns())) {
            actions.accept(ActionType.CreateOrReplaceUpdateTriggerFunc);
            changed = true;
        }

        return changed;
    }

    private static TableConfig find(List<TableConfig> list, String table) throws JDException {
        return list.stream()
                .filter(t -> table.equals(t.table()))
                .findAny().orElseThrow(() -> new JDException("Table misconfiguration"));

    }

    private static Set<String> diff(Set<String> s1, Set<String> s2) {
        Set<String> result = new HashSet<>(s1);
        result.removeAll(s2);
        return result;
    }

    private static TableConfig empty(String table) {
        return new TableConfig(table, new String[0],
                false, false, false, false, new String[0]);
    }
}
