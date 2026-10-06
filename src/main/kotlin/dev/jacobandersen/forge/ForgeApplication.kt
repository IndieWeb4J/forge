package dev.jacobandersen.forge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ForgeApplication

fun main(args: Array<String>) {
    runApplication<ForgeApplication>(*args)
}
