package dev.jacobandersen.forge

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<ForgeApplication>().with(TestcontainersConfiguration::class).run(*args)
}
