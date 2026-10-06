package dev.jacobandersen.forge

import dev.jacobandersen.forge.config.ForgeProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(ForgeProperties::class)
class ForgeApplication

fun main(args: Array<String>) {
    runApplication<ForgeApplication>(*args)
}
