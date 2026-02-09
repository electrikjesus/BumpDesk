package com.bass.bumpdesk

data class RadialMenuItem(
    val label: String,
    val iconRes: Int? = null,
    val subItems: List<RadialMenuItem>? = null,
    val action: (() -> Unit)? = null
)
