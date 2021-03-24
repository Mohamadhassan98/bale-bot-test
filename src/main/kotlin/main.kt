import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook
import org.telegram.telegrambots.meta.generics.Webhook
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook
import java.util.*

fun main() {
    val port = System.getenv("PORT")
    val webhook = DefaultWebhook()
    webhook.setInternalUrl("http://0.0.0.0:$port")
    val setWebhook = SetWebhook
        .builder()
        .url("https://promisedsaviourbot.herokuapp.com")
        .build()
    val botApi = TelegramBotsApi(DefaultBotSession::class.java, webhook)
    botApi.registerBot(Bot(), setWebhook)
}