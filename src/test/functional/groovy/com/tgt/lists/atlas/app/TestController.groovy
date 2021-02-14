package com.tgt.lists.atlas.app

import com.tgt.lists.atlas.api.domain.model.entity.ListEntity
import com.tgt.lists.atlas.api.persistence.cassandra.ListRepository
import com.tgt.lists.atlas.api.service.CreateListService
import com.tgt.lists.atlas.api.transport.ListRequestTO
import com.tgt.lists.atlas.api.transport.ListResponseTO
import io.micronaut.http.annotation.*
import reactor.core.publisher.Mono

import javax.inject.Inject

@Controller("/test")
class TestController {

    ListRepository listRepository
    CreateListService createListService

    @Inject
    TestController(ListRepository listRepository, CreateListService createListService) {
        this.listRepository = listRepository
        this.createListService = createListService
    }

    @Post(value="/list/{guestId}")
    Mono<ListResponseTO> saveList(@PathVariable("guestId") String guestId,
                                  @Body ListRequestTO listRequestTO
    ) {
        return createListService.createList(guestId, listRequestTO)
    }

    @Get(value="/list/{listId}")
    Mono<ListEntity> getList(UUID listId) {
        return listRepository.findListById(listId)
    }
}
