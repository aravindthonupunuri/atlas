package com.tgt.lists.atlas.api.domain

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@ConfigurationProperties("list")
interface Configuration {
    @get:NotBlank
    val listType: String

    @get:Bindable(defaultValue = "50")
    @get:NotNull
    val maxListsCount: Int

    @get:Bindable(defaultValue = "100")
    @get:NotNull
    val maxCompletedItemsCount: Int

    @get:Bindable(defaultValue = "100")
    @get:NotNull
    val maxPendingItemsCount: Int

    @get:Bindable(defaultValue = "false")
    @get:NotNull
    val listItemsDedupe: Boolean

    @get:Bindable(defaultValue = "false")
    @get:NotNull
    val completedListItemsDedupeReplace: Boolean

    @get:Bindable(defaultValue = "false")
    @get:NotNull
    val pendingListRollingUpdate: Boolean

    @get:Bindable(defaultValue = "false")
    @get:NotNull
    val fixedDefaultList: Boolean

    @get:Bindable(defaultValue = "0")
    val pageSize: Int?

    @get:Bindable(defaultValue = "2")
    val purgeExecutionHourOfDay: Int?

    @get:Bindable(defaultValue = "1")
    val purgeExecutionMinuteBlockOfHour: Long

    @get:Bindable(defaultValue = "1")
    val testModeExpiration: Long
}