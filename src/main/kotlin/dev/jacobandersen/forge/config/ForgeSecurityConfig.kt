package dev.jacobandersen.forge.config

import dev.jacobandersen.forge.micropub.MicropubTokenFilter
import dev.jacobandersen.sigil.client.TokenIntrospector
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

/**
 * Forge is a stateless facade: no sessions, no CSRF. The Micropub token filter
 * gates `/micropub`; every other path is permitted (actuator health, etc.).
 */
@Configuration
@EnableWebSecurity
class ForgeSecurityConfig {
    @Bean
    fun micropubTokenFilter(
        introspector: ObjectProvider<TokenIntrospector>,
        properties: ForgeProperties,
        objectMapper: ObjectMapper,
    ): MicropubTokenFilter = MicropubTokenFilter(introspector, properties, objectMapper)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        micropubTokenFilter: MicropubTokenFilter,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(micropubTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
