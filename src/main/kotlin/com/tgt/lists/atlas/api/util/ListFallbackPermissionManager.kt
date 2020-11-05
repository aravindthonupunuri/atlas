package com.tgt.lists.atlas.api.util

import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.common.components.exception.BaseErrorCodes
import com.tgt.lists.common.components.exception.ForbiddenException
import com.tgt.lists.common.components.exception.ResourceNotFoundException
import com.tgt.lists.common.components.filters.auth.permissions.BaseListPermissionManager
import com.tgt.lists.common.components.filters.auth.permissions.ListPermissionManager
import com.tgt.lists.common.components.util.OpenAnnotation
import io.micronaut.http.HttpMethod
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.util.*

/**
 * This is a fallback list permission manager which tries to find cassandra list record for given list id and compare
 * guestId on that record with incoming userId.
 * @return true (userId ==  guestId) | false (userId !=  guestId)
 */
@OpenAnnotation
class ListFallbackPermissionManager(
    private val listRepository: ListRepository
) : ListPermissionManager, BaseListPermissionManager() {
    override fun authorize(userId: String, listId: UUID, requestMethod: HttpMethod): Mono<Boolean> {
        return listRepository.findListById(listId)
                .switchIfEmpty {
                    // micronaut DefaultHttpClient maps 404 to Mono.empty()
                    // whereas all other 4xx/5xx maps to Mono.error()
                    throw ResourceNotFoundException(BaseErrorCodes.RESOURCE_NOT_FOUND_ERROR_CODE(listOf("List $listId not found")))
                }
                .map {
                    if (!userId.equals(it.guestId))
                        throw ForbiddenException(BaseErrorCodes.FORBIDDEN_ERROR_CODE(listOf("User is not allowed to access List $listId")))
                    else
                        true
                }
                .doOnError { handleError(it) }
    }
}