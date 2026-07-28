package org.commonlink.dto

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
    val first: Boolean,
    val last: Boolean,
)

fun <T : Any> Page<T>.toPageResponse() = PageResponse(
    content = content,
    totalElements = totalElements,
    totalPages = totalPages,
    number = number,
    size = size,
    first = isFirst,
    last = isLast,
)
