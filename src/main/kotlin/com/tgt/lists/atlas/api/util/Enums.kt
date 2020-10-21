package com.tgt.lists.atlas.api.util

enum class ItemIncludeFields {
    ALL, PENDING, COMPLETED
}

enum class ItemSortFieldGroup(val value: String) {
    ITEM_TITLE("item_title"),
    ITEM_POSITION("item_position"),
    ADDED_DATE("added_date"),
    ITEM_PATH("item_path"),
    LAST_MODIFIED_DATE("last_modified_date");
}

enum class ItemSortOrderGroup(val value: String) {
    ASCENDING("ASC"),
    DESCENDING("DESC");
}

enum class ItemType(val value: String) {
    TCIN("tcn"),
    OFFER("ofr"),
    GENERIC_ITEM("itm");
}

enum class UnitOfMeasure {
    EACHES;
}

enum class ListSortFieldGroup(val value: String) {
    LIST_TITLE("list_title"),
    LIST_POSITION("list_position"),
    ADDED_DATE("added_date"),
    LAST_MODIFIED_DATE("last_modified_date");
}

enum class ListSortOrderGroup(val value: String) {
    ASCENDING("asc"),
    DESCENDING("desc");
}

enum class Direction(val value: String) {
    ABOVE("above"),
    BELOW("below")
}

enum class LIST_STATUS(val value: String) {
    PENDING("pending"),
    COMPLETED("completed")
}

enum class LIST_ITEM_STATE {
    PENDING,
    COMPLETED
}

enum class LIST_MARKER(val value: String) {
    DEFAULT("D") // default list marker
}

enum class LIST_STATE(val value: String) {
    ACTIVE("A"), // active
    INACTIVE("I") // inactive
}
