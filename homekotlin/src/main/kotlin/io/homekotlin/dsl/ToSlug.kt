package io.homekotlin.dsl

fun String.toSlug(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
