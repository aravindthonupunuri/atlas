package com.tgt.lists.atlas.api.util

import com.tgt.lists.atlas.api.transport.ListItemMetaDataTO
import com.tgt.lists.atlas.api.transport.ListMetaDataTO
import com.tgt.lists.atlas.api.transport.UserItemMetaDataTO
import com.tgt.lists.atlas.api.transport.UserMetaDataTO
import spock.lang.Specification

/*
Test Serialization and Deserialization of metadata between cart and list
 */
class MetaDataTest extends Specification {

    // =========== List Metadata tests =========== //

    def "test ListMetaData deserialization from Cart metadata"() {
        given:
        Boolean defaultList = true
        String listStatus = "PENDING"

        Map<String, Object> cartMetaData = [:]
        cartMetaData.put(Constants.LIST_METADATA, '{"default_list": "'+defaultList+'", "list_status":"'+listStatus+'"}')
        cartMetaData.put(Constants.USER_METADATA, '{"user_meta_data":null}')

        when:
        ListMetaDataTO listMetaDataTO = MetaDataKt.getListMetaDataFromCart(cartMetaData)

        then:
        listMetaDataTO.defaultList == defaultList
        listMetaDataTO.listStatus.name() == listStatus
    }


    def "test List User MetaData deserialization from Cart metadata"() {
        given:
        String username = "testuser"
        Boolean defaultList = true
        String listStatus = "PENDING"

        Map<String, Object> cartMetaData = [:]
        cartMetaData.put(Constants.LIST_METADATA, '{"default_list": "'+defaultList+'", "list_status":"'+listStatus+'"}')
        cartMetaData.put(Constants.USER_METADATA, '{"user_meta_data":{"username":"'+username+'"}}')

        when:
        UserMetaDataTO userMetaDataTO = MetaDataKt.getUserMetaDataFromCart(cartMetaData)

        then:
        userMetaDataTO.userMetaData.get("username") == username
    }

    /*
    Test the scenario when a property data exists in cart metadata but has been deleted from ListMetaDataTO
     */
    def "test ListMetaData deserialization with extra/unknown property in Cart metadata"() {
        given:
        Boolean defaultList = true
        String listStatus = "PENDING"

        Map<String, Object> cartMetaData = [:]
        cartMetaData.put(Constants.LIST_METADATA, '{"default_list": "'+defaultList+'", "list_status":"'+listStatus+'", "extra":"1"}')
        cartMetaData.put(Constants.USER_METADATA, '{"user_meta_data":null}')

        when:
        ListMetaDataTO listMetaDataTO = MetaDataKt.getListMetaDataFromCart(cartMetaData)

        then:
        listMetaDataTO.defaultList == defaultList
        listMetaDataTO.listStatus.name() == listStatus
    }

    /*
    Test the scenario when a property in ListMetaDataTO is updated to a different name,
    and deserialize the older property data in cart metadata to new property name by enabling multiple json token names for updated property via @JsonAlias
     */
    def "test ListMetaData deserialization with updated property name (default_list_ind) in Cart metadata"() {
        given:
        Boolean defaultList = true
        String listStatus = "PENDING"

        Map<String, Object> cartMetaData = [:]
        cartMetaData.put(Constants.LIST_METADATA, '{"default_list_ind": "'+defaultList+'", "list_status":"'+listStatus+'"}')
        cartMetaData.put(Constants.USER_METADATA, '{"user_meta_data":null}')

        when:
        ListMetaDataTO listMetaDataTO = MetaDataKt.getListMetaDataFromCart(cartMetaData)

        then:
        listMetaDataTO.defaultList == defaultList
        listMetaDataTO.listStatus.name() == listStatus
    }

    def "test ListMetaData serialization to Cart metadata"() {
        given:
        ListMetaDataTO listMetaDataTO = new ListMetaDataTO(true, LIST_STATUS.PENDING)

        when:
        Map<String, Object> serializedData = MetaDataKt.setCartMetaDataFromList(listMetaDataTO.defaultList, listMetaDataTO.listStatus, null)

        then:
        serializedData.get("list_metadata") == '{"default_list":'+listMetaDataTO.defaultList+',"list_status":"'+listMetaDataTO.listStatus.name()+'"}'
        serializedData.get("user_metadata") == '{"user_meta_data":null}'
    }

    def "test ListMetaData serialization to Cart metadata with user metadata"() {
        given:
        ListMetaDataTO listMetaDataTO = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        Map<String, Object> userMetaData = new HashMap<>()
        userMetaData.put("username", "testname")

        when:
        Map<String, Object> serializedData = MetaDataKt.setCartMetaDataFromList(listMetaDataTO.defaultList, listMetaDataTO.listStatus, userMetaData)

        then:
        serializedData.get("list_metadata") == '{"default_list":'+listMetaDataTO.defaultList+',"list_status":"'+listMetaDataTO.listStatus.name()+'"}'
        serializedData.get("user_metadata") == '{"user_meta_data":{"username":"testname"}}'
    }

    def "test ListMetaData serialization to Cart metadata with only user metadata"() {
        given:
        Map<String, Object> userMetaData = new HashMap<>()
        userMetaData.put("username", "testname")

        when:
        Map<String, Object> serializedData = MetaDataKt.setCartUserMetaDataFromList(userMetaData)

        then:
        serializedData.get("user_metadata") == '{"user_meta_data":{"username":"testname"}}'
    }




    // =========== ListItem Metadata tests =========== //



    def "test ListItemMetaData deserialization from CartItem metadata"() {
        given:
        ItemType itemType = ItemType.TCIN
        LIST_ITEM_STATE itemState = LIST_ITEM_STATE.PENDING

        Map<String, Object> cartItemMetadata = [:]
        cartItemMetadata.put(Constants.LIST_ITEM_METADATA, '{"item_type":"'+itemType+'","item_state":"'+itemState.name()+'"}')
        cartItemMetadata.put(Constants.USER_ITEM_METADATA, '{"user_meta_data":null}')

        when:
        ListItemMetaDataTO listItemMetaDataTO = MetaDataKt.getListItemMetaDataFromCart(cartItemMetadata)

        then:
        listItemMetaDataTO.itemType == itemType
        listItemMetaDataTO.itemState == itemState
    }

    def "test ListItem User MetaData deserialization from CartItem metadata"() {
        given:
        String username = "testuser"
        ItemType itemType = ItemType.TCIN
        LIST_ITEM_STATE itemState = LIST_ITEM_STATE.PENDING

        Map<String, Object> cartItemMetadata = [:]
        cartItemMetadata.put(Constants.LIST_ITEM_METADATA, '{"item_type":"'+itemType+'","item_state":"'+itemState.name()+'"}')
        cartItemMetadata.put(Constants.USER_ITEM_METADATA, '{"user_meta_data":{"username":"'+username+'"}}')

        when:
        UserItemMetaDataTO userItemMetaDataTO = MetaDataKt.getUserItemMetaDataFromCart(cartItemMetadata)

        then:
        userItemMetaDataTO.userMetaData.get("username") == username
    }

    /*
    Test the scenario when a property data exists in cartitem metadata but has been deleted from ListItemMetaDataTO
    */
    def "test ListItemMetaData deserialization with extra/unknown property in CartItem metadata"() {
        given:
        ItemType itemType = ItemType.TCIN
        LIST_ITEM_STATE itemState = LIST_ITEM_STATE.PENDING

        Map<String, Object> cartItemMetadata = [:]
        cartItemMetadata.put(Constants.LIST_ITEM_METADATA, '{"item_type":"'+itemType+'","item_state":"'+itemState.name()+'","extra":"1"}')
        cartItemMetadata.put(Constants.USER_ITEM_METADATA, '{"user_meta_data":null}')

        when:
        ListItemMetaDataTO listItemMetaDataTO = MetaDataKt.getListItemMetaDataFromCart(cartItemMetadata)

        then:
        listItemMetaDataTO.itemType == itemType
        listItemMetaDataTO.itemState == itemState
    }

    def "test ListItemMetaData serialization to CartItem metadata"() {
        given:
        ListItemMetaDataTO listItemMetaDataTO = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        when:
        Map<String, Object> serializedData = MetaDataKt.setCartItemMetaDataForListItem(listItemMetaDataTO.itemType, listItemMetaDataTO.itemState, null)

        then:
        serializedData.get("list_item_metadata") == '{"item_type":"'+listItemMetaDataTO.itemType+'","item_state":"'+listItemMetaDataTO.itemState.name()+'"}'
        serializedData.get("user_item_metadata") == '{"user_meta_data":null}'
    }

    def "test ListItemMetaData serialization to CartItem metadata with user metadata"() {
        given:
        ListItemMetaDataTO listItemMetaDataTO = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        Map<String, Object> userMetaData = new HashMap<>()
        userMetaData.put("username", "testname")

        when:
        Map<String, Object> serializedData = MetaDataKt.setCartItemMetaDataForListItem(listItemMetaDataTO.itemType, listItemMetaDataTO.itemState, userMetaData)

        then:
        serializedData.get("list_item_metadata") == '{"item_type":"'+listItemMetaDataTO.itemType+'","item_state":"'+listItemMetaDataTO.itemState.name()+'"}'
        serializedData.get("user_item_metadata") == '{"user_meta_data":{"username":"testname"}}'
    }

    def "test ListItemMetaData serialization to CartItem metadata with only user metadata"() {
        given:
        Map<String, Object> userMetaData = new HashMap<>()
        userMetaData.put("username", "testname")

        when:
        Map<String, Object> serializedData = MetaDataKt.setCartUserItemMetaDataForListItem(userMetaData)

        then:
        serializedData.get("user_item_metadata") == '{"user_meta_data":{"username":"testname"}}'
    }
}
