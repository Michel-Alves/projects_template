package {{tld}}.{{author}}.{{app_name}}.commons

import java.time.Instant

fun interface Clock {
    fun now(): Instant

    companion object {
        val SYSTEM: Clock = Clock { Instant.now() }
    }
}
