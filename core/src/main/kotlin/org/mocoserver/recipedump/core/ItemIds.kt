package org.mocoserver.recipedump.core

private val itemIdPattern = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")

internal fun isItemId(value: String): Boolean = itemIdPattern.matches(value)
