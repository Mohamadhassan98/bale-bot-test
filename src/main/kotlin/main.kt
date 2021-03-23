import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.util.*

fun main() {
    val botApi = TelegramBotsApi(DefaultBotSession::class.java)
    botApi.registerBot(Bot())
}