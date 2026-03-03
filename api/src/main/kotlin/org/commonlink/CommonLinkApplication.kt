package com.commonlink

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CommonLinkApplication

fun main(args: Array<String>) {
    runApplication<CommonLinkApplication>(*args)
}
