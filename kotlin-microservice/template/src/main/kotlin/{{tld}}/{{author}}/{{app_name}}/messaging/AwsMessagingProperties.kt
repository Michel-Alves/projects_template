package {{tld}}.{{author}}.{{app_name}}.messaging

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aws.messaging")
data class AwsMessagingProperties(
    val region: String = "us-east-1",
    val endpointOverride: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val topicArn: String = "",
    val queueUrl: String = "",
    val pollWaitSeconds: Int = 10,
    val maxMessagesPerPoll: Int = 10,
)
