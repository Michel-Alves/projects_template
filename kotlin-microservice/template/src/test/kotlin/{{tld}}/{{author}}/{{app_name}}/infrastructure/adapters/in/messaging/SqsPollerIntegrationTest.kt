package {{tld}}.{{author}}.{{app_name}}.infrastructure.adapters.`in`.messaging

import {{tld}}.{{author}}.{{app_name}}.application.messaging.MessageHandler
import {{tld}}.{{author}}.{{app_name}}.infrastructure.adapters.out.messaging.SnsPublisher
import {{tld}}.{{author}}.{{app_name}}.infrastructure.config.AwsMessagingProperties
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.CreateTopicRequest
import software.amazon.awssdk.services.sns.model.SubscribeRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

@Testcontainers
class SqsPollerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val localstack: LocalStackContainer = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8")
        ).withServices(Service.SNS, Service.SQS)
    }

    @Test
    fun `publishes via SNS and consumes via SQS poller`() {
        val creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)
        )
        val region = Region.of(localstack.region)

        val sns = SnsClient.builder()
            .endpointOverride(localstack.endpoint)
            .region(region)
            .credentialsProvider(creds)
            .build()
        val sqs = SqsClient.builder()
            .endpointOverride(localstack.endpoint)
            .region(region)
            .credentialsProvider(creds)
            .build()

        val topicArn = sns.createTopic(CreateTopicRequest.builder().name("it-events").build()).topicArn()
        val queueUrl = sqs.createQueue(CreateQueueRequest.builder().queueName("it-events").build()).queueUrl()
        val queueArn = sqs.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()
        ).attributes()[QueueAttributeName.QUEUE_ARN]!!

        sns.subscribe(
            SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(mapOf("RawMessageDelivery" to "true"))
                .build()
        )

        val props = AwsMessagingProperties(
            region = localstack.region,
            endpointOverride = localstack.endpoint.toString(),
            accessKey = localstack.accessKey,
            secretKey = localstack.secretKey,
            topicArn = topicArn,
            queueUrl = queueUrl,
            pollWaitSeconds = 1,
            maxMessagesPerPoll = 10,
        )

        val received = AtomicReference<String?>(null)
        val handler = MessageHandler { body -> received.set(body) }
        val poller = SqsPoller(sqs, props, handler)

        SnsPublisher(sns, props).publish("hello-from-test")

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            poller.poll()
            assertThat(received.get()).isEqualTo("hello-from-test")
        }
    }
}
