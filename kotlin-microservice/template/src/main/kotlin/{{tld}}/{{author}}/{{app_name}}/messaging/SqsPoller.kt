package {{tld}}.{{author}}.{{app_name}}.messaging

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

@Component
class SqsPoller(
    private val sqsClient: SqsClient,
    private val props: AwsMessagingProperties,
    private val handler: MessageHandler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${aws.messaging.poll-fixed-delay-ms:1000}")
    fun poll() {
        if (props.queueUrl.isBlank()) return

        val response = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(props.queueUrl)
                .maxNumberOfMessages(props.maxMessagesPerPoll)
                .waitTimeSeconds(props.pollWaitSeconds)
                .build()
        )

        for (message in response.messages()) {
            try {
                handler.handle(message.body())
                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(props.queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build()
                )
            } catch (e: Exception) {
                log.error("Failed to process SQS message {}", message.messageId(), e)
            }
        }
    }
}
