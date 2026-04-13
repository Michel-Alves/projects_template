package {{tld}}.{{author}}.{{app_name}}.messaging

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SampleMessageHandler : MessageHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(body: String) {
        log.info("Received SQS message: {}", body)
    }
}
