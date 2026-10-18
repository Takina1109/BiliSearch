package com.takina.bilisearch.data.net

class BiliException(
    val code: Int,
    message: String
) : Exception(message)
