package {{tld}}.{{author}}.{{app_name}}.application.messaging

fun interface MessageHandler {
    fun handle(body: String)
}
