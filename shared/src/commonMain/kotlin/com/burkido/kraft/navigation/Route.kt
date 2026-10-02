package com.burkido.kraft.navigation

import com.burkido.kraft.library.LibraryId

/** Back stack keys. The stack is a plain state list, so no serializers are needed. */
sealed interface Route {
    data object Landing : Route

    data class Library(val id: LibraryId) : Route

    data object Licenses : Route
}
