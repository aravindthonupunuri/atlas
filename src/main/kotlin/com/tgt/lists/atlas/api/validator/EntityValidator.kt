package com.tgt.lists.atlas.api.validator

import com.tgt.lists.common.components.exception.BaseErrorCodes.RESPONSE_BODY_VIOLATION_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode
import com.tgt.lists.common.components.exception.InternalServerException
import javax.validation.Validation

private val validator = Validation.buildDefaultValidatorFactory().validator

fun <T> validate(entity: T): T {
    val fieldErrors = validator.validate(entity).map { it.message }.toList()
    if (!fieldErrors.isNullOrEmpty()) {
        throw InternalServerException(ErrorCode(RESPONSE_BODY_VIOLATION_ERROR_CODE.first, RESPONSE_BODY_VIOLATION_ERROR_CODE.second, fieldErrors))
    }
    return entity
}

fun <T> validate(entities: List<T>): List<T> {
    val fieldErrors = entities.flatMap { validator.validate(it).map { it.message } }.toList()
    if (!fieldErrors.isNullOrEmpty()) {
        throw InternalServerException(ErrorCode(RESPONSE_BODY_VIOLATION_ERROR_CODE.first, RESPONSE_BODY_VIOLATION_ERROR_CODE.second, fieldErrors))
    }
    return entities
}