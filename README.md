# atlas
[![Build Status](https://drone6.target.com/api/badges/Backpack-Platform/atlas/status.svg)](https://drone6.target.com/Backpack-Platform/atlas)
[![ktlint](https://img.shields.io/badge/code%20style-%E2%9D%A4-FF4081.svg)](https://ktlint.github.io/)

Atlas provides foundation for building various list types

# Configuration

Following configuration can be specified via application yml
```
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
```
