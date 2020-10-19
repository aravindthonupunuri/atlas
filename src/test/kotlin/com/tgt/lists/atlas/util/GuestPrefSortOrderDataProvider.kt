package com.tgt.lists.atlas.util

import com.tgt.lists.atlas.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.atlas.api.util.Direction
import java.util.*

class GuestPrefSortOrderDataProvider {

    fun getEditItemSortOrderRequestTO(
        listId: UUID,
        primaryItemId: UUID,
        secondaryItemId: UUID,
        direction: Direction
    ): EditItemSortOrderRequestTO {
        return EditItemSortOrderRequestTO(listId = listId, primaryItemId = primaryItemId,
            secondaryItemId = secondaryItemId, direction = direction)
    }
}
