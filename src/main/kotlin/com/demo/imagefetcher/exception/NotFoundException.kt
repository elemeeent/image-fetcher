package com.demo.imagefetcher.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Object not found or no permissions")
class NotFoundException(
    override val message: String
) : RuntimeException()