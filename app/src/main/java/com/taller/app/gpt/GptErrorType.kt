package com.taller.app.gpt

enum class GptErrorType {
    NOT_CONFIGURED,
    NOT_ENABLED,
    NO_NETWORK,
    TIMEOUT,
    HTTP_401,
    HTTP_403,
    HTTP_429,
    HTTP_ERROR,
    EMPTY_RESPONSE,
    PARSE_ERROR,
    UNKNOWN
}
