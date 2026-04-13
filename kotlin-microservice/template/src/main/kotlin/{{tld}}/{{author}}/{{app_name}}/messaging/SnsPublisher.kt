package {{tld}}.{{author}}.{{app_name}}.messaging

import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse

@Component
class SnsPublisher(
    private val snsClient: SnsClient,
    private val props: AwsMessagingProperties,
) {
    fun publish(message: String): PublishResponse =
        snsClient.publish(
            PublishRequest.builder()
                .topicArn(props.topicArn)
                .message(message)
                .build()
        )
}
