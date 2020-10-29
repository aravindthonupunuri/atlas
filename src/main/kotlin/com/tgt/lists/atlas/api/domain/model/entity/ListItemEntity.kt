package com.tgt.lists.atlas.api.domain.model.entity

import com.datastax.oss.driver.api.mapper.annotations.*
import com.datastax.oss.driver.api.mapper.entity.naming.NamingConvention
import com.tgt.lists.atlas.api.util.AppErrorCodes.LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE
import com.tgt.lists.atlas.api.util.ItemType
import com.tgt.lists.common.components.exception.InternalServerException
import java.time.Instant
import java.util.*

@CqlName("lists")
@Entity
@NamingStrategy(convention = [NamingConvention.SNAKE_CASE_INSENSITIVE])
data class ListItemEntity(
    @PartitionKey
    var id: UUID? = null,

    @ClusteringColumn(0)
    override var itemState: String? = null,

    @ClusteringColumn(1)
    override var itemId: UUID? = null,

    override var itemRefId: String? = null,
    override var itemType: String? = null,
    override var itemTcin: String? = null,
    override var itemTitle: String? = null,
    override var itemDpci: String? = null,
    override var itemBarcode: String? = null,
    override var itemDesc: String? = null,
    override var itemChannel: String? = null,
    override var itemSubchannel: String? = null,
    override var itemMetadata: String? = null,
    override var itemNotes: String? = null,
    override var itemQty: Int? = null,
    override var itemQtyUom: String? = null,
    override var itemReqQty: Int? = null,
    override var itemCreatedAt: Instant? = null,
    override var itemUpdatedAt: Instant? = null
) : IListItem {

    fun validate(): ListItemEntity {
        return when (this.itemType) {
            ItemType.GENERIC_ITEM.value -> validateGenericItem(this)
            ItemType.TCIN.value -> validateTcinItem(this)
            ItemType.OFFER.value -> validateOfferItem(this)
            else -> throw InternalServerException(LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE(arrayListOf("Invalid Item Type")))
        }
    }

    private fun validateTcinItem(listItemEntity: ListItemEntity): ListItemEntity {
        var item = listItemEntity
        if (item.itemTcin == null || item.itemTcin?.toIntOrNull() == null) throw InternalServerException(LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE(arrayListOf("Required field tcin is missing")))
        if (item.itemTitle != null) {
            item = item.copy(itemTitle = "") // Updating the item title to empty since TCIN item type cannot have a title. We use NullSavingStrategy.DO_NOT_SET so passing an empty String instead of NULL
        }
        return item
    }

    private fun validateGenericItem(listItemEntity: ListItemEntity): ListItemEntity {
        var item = listItemEntity
        val itemTitle: String = item.itemTitle ?: throw InternalServerException(LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE(arrayListOf("Required field item title is missing")))
        if (itemTitle.trim().toIntOrNull() != null) throw InternalServerException(LIST_ITEM_ENTITY_VIOLATION_ERROR_CODE(arrayListOf("Invalid item title")))
        if (item.itemTcin != null) {
            item = item.copy(itemTcin = "") // Updating tcin to empty since GENERIC item type cannot have a tcin value. We use NullSavingStrategy.DO_NOT_SET so passing an empty String instead of NULL
        }
        return item
    }

    private fun validateOfferItem(listItemEntity: ListItemEntity): ListItemEntity {
        var item = listItemEntity
        if (item.itemTcin != null) {
            item = item.copy(itemTcin = "") // Updating tcin to empty since OFFER item type cannot have a tcin value. We use NullSavingStrategy.DO_NOT_SET so passing an empty String instead of NULL
        }
        if (item.itemTitle != null) {
            item = item.copy(itemTitle = "") // Updating the item title to empty since OFFER item type cannot have a title. We use NullSavingStrategy.DO_NOT_SET so passing an empty String instead of NULL
        }
        return item
    }
}