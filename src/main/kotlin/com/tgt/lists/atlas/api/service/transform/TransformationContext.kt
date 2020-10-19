package com.tgt.lists.atlas.api.service.transform

/**
 * Context carries pipeline configuration as well as provides a mean for individual steps to send any additional
 * values across pipeline all the way upto the caller.
 */
data class TransformationContext(
    val transformationPipelineConfiguration: TransformationPipelineConfiguration
) {
    private val contextData: MutableMap<String, Any> = mutableMapOf<String, Any>()

    fun addContextValue(key: String, value: Any) {
        if (!contextData.containsKey(key)) {
            contextData.put(key, value)
        } else
            throw RuntimeException("Existing key $key can't be overriden")
    }

    fun getContextValue(key: String): Any {
        return contextData.getValue(key)
    }

    fun getAllContextKeys(): Set<String> {
        return contextData.keys
    }
}