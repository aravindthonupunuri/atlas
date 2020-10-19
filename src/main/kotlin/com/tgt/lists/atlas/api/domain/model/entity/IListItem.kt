package com.tgt.lists.atlas.api.domain.model.entity

import java.time.Instant
import java.util.*

/**
 * Interface for List Item data
 */
interface IListItem {
    var itemId: UUID?
    var itemState: String?
    var itemRefId: String?
    var itemType: String?
    var itemTcin: String?
    var itemDpci: String?
    var itemBarcode: String?
    var itemDesc: String?
    var itemChannel: String?
    var itemSubchannel: String?
    var itemMetadata: String?
    var itemNotes: String?
    var itemQty: Int?
    var itemQtyUom: String?
    var itemReqQty: Int?
    var itemCreatedAt: Instant?
    var itemUpdatedAt: Instant?
}