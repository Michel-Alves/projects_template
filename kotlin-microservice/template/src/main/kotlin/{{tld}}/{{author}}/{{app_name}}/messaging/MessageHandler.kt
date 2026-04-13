package {{tld}}.{{author}}.{{app_name}}.messaging

fun interface MessageHandler {
    fun handle(body: String)
}
