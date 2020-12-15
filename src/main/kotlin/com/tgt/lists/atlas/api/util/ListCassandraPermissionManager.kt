package com.tgt.lists.atlas.api.util

import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.common.components.exception.BaseErrorCodes.FORBIDDEN_ERROR_CODE
import com.tgt.lists.common.components.exception.BaseErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE
import com.tgt.lists.common.components.exception.ErrorCode
import com.tgt.lists.common.components.exception.ForbiddenException
import com.tgt.lists.common.components.exception.ResourceNotFoundException
import com.tgt.lists.common.components.filters.auth.Constants.Companion.ANONYMOUS_MEMBER_ID
import com.tgt.lists.common.components.filters.auth.permissions.BaseListPermissionManager
import com.tgt.lists.common.components.util.OpenAnnotation
import io.micronaut.http.HttpMethod
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*

/**
 * This list permission manager authorizes listId access by finding corresponding cassandra list record and compare
 * record's guestId with given userId.
 * @return true (userId ==  guestId) | false (userId !=  guestId)
 */
@OpenAnnotation
class ListCassandraPermissionManager(
    private val listRepository: ListRepository
) : BaseListPermissionManager() {
    override fun authorize(userId: String, listId: UUID, requestMethod: HttpMethod): Mono<Boolean> {
        // if user is an anonymous user, we can't authorize using cassandra
        if (userId == ANONYMOUS_MEMBER_ID) {
            return Mono.error(ForbiddenException(ErrorCode(FORBIDDEN_ERROR_CODE, listOf("$ANONYMOUS_MEMBER_ID user is not allowed to access List $listId"))))
        }

        return listRepository.findListById(listId)
                .switchIfEmpty {
                    // micronaut DefaultHttpClient maps 404 to Mono.empty()
                    // whereas all other 4xx/5xx maps to Mono.error()
                    throw ResourceNotFoundException(ErrorCode(RESOURCE_NOT_FOUND_ERROR_CODE, listOf("List $listId not found")))
                }
                .map {
                    if (!userId.equals(it.guestId))
                        throw ForbiddenException(ErrorCode(FORBIDDEN_ERROR_CODE, listOf("User is not allowed to access List $listId")))
                    else
                        true
                }
                .doOnError { handleError(it) }
    }

    override fun useFallbackForResourceNotFound(): Boolean {
        return false
    }

    override fun useFallbackForFailedAccess(): Boolean {
        return false
    }
}