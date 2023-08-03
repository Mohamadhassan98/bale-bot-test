import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

fun main() {
    val botApi = TelegramBotsApi(DefaultBotSession::class.java)
    val options = DefaultBotOptions()
    options.baseUrl = "https://tapi.bale.ai/"
    botApi.registerBot(Bot(options))
}