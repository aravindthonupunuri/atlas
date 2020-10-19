package com.tgt.lists.atlas.api.service.transform

import com.tgt.lists.atlas.api.service.transform.list_items.ListItemsTransformationPipelineConfiguration
import spock.lang.Shared
import spock.lang.Specification

class TransformationContextTest extends Specification {

    @Shared
    TransformationContext transformationContext = new TransformationContext(new ListItemsTransformationPipelineConfiguration(null,null))

    def "test add context key"() {

        when:
        transformationContext.addContextValue("PENDING_STATE", "pending")

        then:
        transformationContext.getContextValue("PENDING_STATE") == "pending"
    }

    def "test add context key when key already exists"() {
        when:
        transformationContext.addContextValue("PENDING_STATE", "pending2")

        then:
        thrown(RuntimeException)
    }

    def "test get all context keys"() {

        when:
        transformationContext.addContextValue("COMPLETED_STATE", "completed")
        def allKeys = transformationContext.getAllContextKeys()

        then:
        allKeys.size() == 2
        allKeys[0] == "PENDING_STATE"
        allKeys[1] == "COMPLETED_STATE"
    }
}
