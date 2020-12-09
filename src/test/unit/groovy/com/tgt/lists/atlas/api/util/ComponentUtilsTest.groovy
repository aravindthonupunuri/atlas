package com.tgt.lists.atlas.api.util

import com.tgt.lists.common.components.util.ComponentUtils
import spock.lang.Specification

class ComponentUtilsTest extends Specification {

    def "test error duplicates with no duplicate scenario"() {
        given:
        def errorCodesClassList = ["com.tgt.lists.common.components.exception.BaseErrorCodes",
                                       "com.tgt.lists.atlas.api.util.ErrorCodes"]

        when:
        def duplicates = ComponentUtils.hasDuplicateErrorCodes(errorCodesClassList)

        then:
        !duplicates
    }
}
