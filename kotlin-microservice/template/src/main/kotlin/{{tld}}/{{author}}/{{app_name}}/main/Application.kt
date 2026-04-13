package {{tld}}.{{author}}.{{app_name}}.main

import {{tld}}.{{author}}.{{app_name}}.commons.Clock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["{{tld}}.{{author}}.{{app_name}}"])
@ConfigurationPropertiesScan(basePackages = ["{{tld}}.{{author}}.{{app_name}}"])
@EnableScheduling
class Application {
    @Bean
    fun clock(): Clock = Clock.SYSTEM
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
