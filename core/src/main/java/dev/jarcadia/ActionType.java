package dev.jarcadia;

enum ActionType {

    InstallDmlEvents,
    InstallRealTimeRooms,

    CreateRealTimeRoomLinkTable,

    CreateOrReplaceInsertTriggerFunc,
    CreateOrReplaceUpdateTriggerFunc,
    CreateOrReplaceUpdateTriggerDiffFunc,
    CreateOrReplaceDeleteTriggerFunc,

    CreateInsertTrigger,
    CreateUpdateTrigger,
    CreateDeleteTrigger,

    DropInsertTrigger,
    DropUpdateTrigger,
    DropDeleteTrigger,

    DropInsertTriggerFunc,
    DropUpdateTriggerFunc,
    DropUpdateTriggerDiffFunc,
    DropDeleteTriggerFunc,

    DropRealTimeRoomLinkTable,
    DeleteRealTimeRoomsForTable,

    UninstallDmlEvents,
    UninstallRealTimeRooms,

    InsertTableConfigRow,
    UpdateTableConfigRow,
    DeleteTableConfigRow
}
