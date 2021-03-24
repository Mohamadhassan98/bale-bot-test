import model.Promise
import model.PromiseType
import model.UserStatus
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.bot.AbilityWebhookBot
import org.telegram.abilitybots.api.objects.*
import org.telegram.abilitybots.api.sender.SilentSender
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.util.*

class Bot : AbilityWebhookBot("1783273982:AAHkGOrp0qh8EBIEix2vH6JEEeHjetNVCtQ", "PromisedSaviourDevBot", "https://promisedsaviourbot.herokuapp.com") {

    override fun creatorId() = 714273093L

    override fun onRegister() {
        super.onRegister()
        silent.send("bot started", creatorId())
        kotlin.concurrent.fixedRateTimer(startAt = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            if (!time.after(Date())) {
                add(Calendar.DATE, 1)
            }
        }.time, period = 24 * 60 * 60 * 1000) {
            val sentMessages = mutableSetOf<Long>()
            val promises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
            promises.entries.filter { (_, userStatus) ->
                userStatus.isReadyToSend
            }.forEach { (userId, userStatus) ->
                silent.send(
                    "${Messages.promiseReminder}\n${
                        Messages.promiseInfoDialog(
                            getPromise(userStatus.promise)!!,
                            userStatus,
                            PromiseType.getType(userStatus.promise / 100)!!,
                            true
                        )
                    }", userId
                )
                sentMessages += userId
            }
            sentMessages.forEach {
                val current = promises[it]!!
                if (current.remainingDays == 1) {
                    promises.remove(it)
                } else {
                    promises[it] = current.copy(remainingDays = current.remainingDays - 1)
                }
            }
        }
    }

    @Suppress("unused")
    val startBot: Ability
        get() = Ability
            .builder()
            .name("start")
            .info(Messages.startInfo)
            .action {
                silent.sendWithInlineKeyboard(Messages.welcome, it.chatId(), PromiseType.values().map { promiseType ->
                    promiseType.persianName to promiseType.name
                }.toMap())
            }
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .build()

    @Suppress("unused")
    val replyToPromiseType: Reply
        get() = Reply.of({ _, it ->
            val promiseType = PromiseType.valueOf(it.callbackQuery.data)
            val data = "${Messages.selectedPromiseType} ${promiseType.persianName}:\n${
                promises.value.first {
                    it.audience == promiseType
                }.promises.joinToString("\n") {
                    "${promiseType.id}${it.id.toString().padStart(2, '0')}-${it.content}"
                }
            }\n${Messages.promiseRegisterHelp}"
            silent.send(data, it.callbackQuery.message.chatId)
        }, Flag.CALLBACK_QUERY, {
            runCatching { PromiseType.valueOf(it.callbackQuery.data) }.isSuccess
        })

    @Suppress("unused")
    val promise: Ability
        get() = Ability
            .builder()
            .name("promise")
            .info(Messages.promiseRegisterInfo)
            .privacy(Privacy.PUBLIC)
            .locality(Locality.USER)
            .input(1)
            .action {
                if (hasPromise(it.user().id)) {
                    silent.send(Messages.alreadyHasPromise, it.chatId())
                    return@action
                }
                val promiseId = it.firstArg()
                if (promiseId.toIntOrNull() == null) {
                    silent.send(Messages.wrongPromise, it.chatId())
                    return@action
                }
                val promise = getPromise(promiseId.toInt())
                if (promise == null) {
                    silent.send(Messages.wrongPromise, it.chatId())
                    return@action
                }
                silent.sendWithInlineKeyboard(Messages.promiseConfirmDialog(
                    promise,
                    promiseId.toInt(),
                    PromiseType.getType(promiseId.toInt() / 100)!!
                ), it.chatId(), mapOf(
                    Messages.confirmPromiseText to "${Messages.confirmPromiseData} $promiseId",
                    Messages.rejectPromiseText to "${Messages.rejectPromiseData} $promiseId"
                ))
            }
            .build()

    @Suppress("unused")
    val replyToPromiseChoose: Reply
        get() = Reply.of({ _, it ->
            if (hasPromise(it.callbackQuery.from.id)) {
                silent.send(Messages.alreadyHasPromise, it.callbackQuery.message.chatId)
                return@of
            }
            val data = it.callbackQuery.data
            val (result, promiseId) = data.split(" ")
            when (result) {
                Messages.confirmPromiseData -> {
                    val userPromises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
                    userPromises[it.callbackQuery.from.id] = UserStatus(promiseId.toInt())
                    silent.send(Messages.promiseConfirmed, it.callbackQuery.message.chatId)
                }
                Messages.rejectPromiseData -> silent.sendWithInlineKeyboard(Messages.promiseRejected,
                    it.callbackQuery.message.chatId,
                    PromiseType.values().map {
                        it.persianName to it.name
                    }.toMap()
                )
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data.startsWith(Messages.confirmPromiseData) || it.callbackQuery.data.startsWith(Messages.rejectPromiseData)
        })

    @Suppress("unused")
    val replyToPromiseModify: Reply
        get() = Reply.of({ _, it ->
            if (!hasPromise(it.callbackQuery.from.id)) {
                silent.send(Messages.noPromiseFound, it.callbackQuery.message.chatId)
                return@of
            }
            when (it.callbackQuery.data) {
                Messages.restartPromiseData -> silent.sendWithInlineKeyboard(
                    Messages.restartPromiseConfirm, it.callbackQuery.message.chatId,
                    mapOf(
                        Messages.restartPromiseConfirmText to Messages.restartPromiseConfirmData,
                        Messages.restartPromiseRejectText to Messages.restartPromiseRejectData
                    )
                )
                Messages.removePromiseData -> silent.sendWithInlineKeyboard(
                    Messages.removePromiseConfirm,
                    it.callbackQuery.message.chatId,
                    mapOf(
                        Messages.removePromiseConfirmText to Messages.removePromiseConfirmData,
                        Messages.removePromiseRejectText to Messages.removePromiseRejectData
                    )
                )
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Messages.removePromiseData || it.callbackQuery.data == Messages.restartPromiseData
        })

    @Suppress("unused")
    val replyToRemovePromise: Reply
        get() = Reply.of({ _, it ->
            if (!hasPromise(it.callbackQuery.from.id)) {
                silent.send(Messages.noPromiseFound, it.callbackQuery.message.chatId)
                return@of
            }
            when (it.callbackQuery.data) {
                Messages.removePromiseConfirmData -> {
                    val promises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
                    promises.remove(it.callbackQuery.from.id)
                    silent.send(Messages.promiseRemoved, it.callbackQuery.message.chatId)
                    silent.send(Messages.noPromiseFound, it.callbackQuery.message.chatId)
                }
                Messages.removePromiseRejectData -> {
                    val promises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
                    val userPromise = promises[it.callbackQuery.from.id]!!
                    silent.sendWithInlineKeyboard(Messages.promiseInfoDialog(
                        getPromise(userPromise.promise)!!,
                        userPromise,
                        PromiseType.getType(userPromise.promise / 100)!!
                    ), it.callbackQuery.message.chatId, mapOf(
                        Messages.removePromiseText to Messages.removePromiseData,
                        Messages.restartPromiseText to Messages.restartPromiseData
                    ))
                }
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Messages.removePromiseRejectData || it.callbackQuery.data == Messages.removePromiseConfirmData
        })

    @Suppress("unused")
    val replyToRestartPromise: Reply
        get() = Reply.of({ _, it ->
            if (!hasPromise(it.callbackQuery.from.id)) {
                silent.send(Messages.noPromiseFound, it.callbackQuery.message.chatId)
                return@of
            }
            when (it.callbackQuery.data) {
                Messages.restartPromiseConfirmData -> {
                    val promises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
                    val userPromise = promises[it.callbackQuery.from.id]!!
                    promises[it.callbackQuery.from.id] = userPromise.copy(remainingDays = 40)
                    silent.send(Messages.promiseRestarted, it.callbackQuery.message.chatId)
                    silent.sendWithInlineKeyboard(Messages.promiseInfoDialog(
                        getPromise(userPromise.promise)!!,
                        userPromise,
                        PromiseType.getType(userPromise.promise / 100)!!
                    ), it.callbackQuery.message.chatId, mapOf(
                        Messages.removePromiseText to Messages.removePromiseData,
                        Messages.restartPromiseText to Messages.restartPromiseData
                    ))
                }
                Messages.restartPromiseRejectData -> {
                    val promises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
                    val userPromise = promises[it.callbackQuery.from.id]!!
                    silent.sendWithInlineKeyboard(Messages.promiseInfoDialog(
                        getPromise(userPromise.promise)!!,
                        userPromise,
                        PromiseType.getType(userPromise.promise / 100)!!
                    ), it.callbackQuery.message.chatId, mapOf(
                        Messages.removePromiseText to Messages.removePromiseData,
                        Messages.restartPromiseText to Messages.restartPromiseData
                    ))
                }
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Messages.restartPromiseRejectData || it.callbackQuery.data == Messages.restartPromiseConfirmData
        })

    @Suppress("unused")
    val statusInfo: Ability
        get() = Ability
            .builder()
            .name("info")
            .info(Messages.statusInfoInfo)
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .action {
                val promises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
                val userPromise = promises[it.user().id]
                if (userPromise == null) {
                    silent.send(Messages.noPromiseFound, it.chatId())
                } else {
                    silent.sendWithInlineKeyboard(Messages.promiseInfoDialog(
                        getPromise(userPromise.promise)!!,
                        userPromise,
                        PromiseType.getType(userPromise.promise / 100)!!
                    ), it.chatId(), mapOf(
                        Messages.removePromiseText to Messages.removePromiseData,
                        Messages.restartPromiseText to Messages.restartPromiseData
                    ))
                }
            }
            .build()

    private fun SilentSender.sendWithInlineKeyboard(
        textMessage: String,
        id: Long,
        buttons: Map<String, String>
    ): Optional<Message>? {
        return execute(SendMessage().apply {
            text = textMessage
            chatId = id.toString()
            replyMarkup = InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                    buttons.map { (text, data) ->
                        InlineKeyboardButton
                            .builder()
                            .text(text)
                            .callbackData(data)
                            .build()
                    }
                )
                .build()
        })
    }

    private fun hasPromise(userId: Long, promiseId: Int? = null): Boolean {
        val userPromises = db.getMap<Long, UserStatus>(Messages.promisesDBMapName)
        val userPromise = userPromises[userId] ?: return false
        return userPromise.promise == (promiseId ?: return true)
    }

    private fun getPromise(promiseId: Int): Promise? {
        val promiseTypeId = promiseId / 100
        val id = promiseId % 100
        val promiseType = promises.value.firstOrNull {
            it.audience == PromiseType.getTypeOrNull(promiseTypeId)
        } ?: return null
        return promiseType.promises.firstOrNull {
            it.id == id
        }
    }
}

object Messages {
    val welcome =
        """|به بات عهد با امام زمان خوش اومدی.
           |توی این بات ما چند تا عهد رو بهت معرفی می‌کنیم تا به دلخواه خودت یکی رو انتخاب کنی. بعد از اون ما تا چهل روز بهت یادآوری می‌کنیم که عهدت با امام زمانت رو فراموش نکنی.
           |عهدهایی که می‌تونی با امام زمان (عج) ببندی می‌تونه در رابطه با سه دسته افراد باشه که برای این که راحت‌تر بتونی عهد مورد نظرت رو پیدا کنی ما هم با همین دسته‌بندی عهدها رو بهت نشون می‌دیم. این سه دسته عبارتند از:
           |۱- در رابطه با خدا
           |۲- در رابطه با مردم
           |۳- در رابطه با خودت
           |برای مشاهده لیست عهدها، یکی از دسته‌ها رو انتخاب کن.
""".trimMargin()
    const val startInfo = "شروع کار با بات"
    const val selectedPromiseType = "نمایش لیست عهدها"
    const val promiseRegisterHelp =
        "برای انتخاب هر یک از عهدها کافیه شماره عهد رو بعد از دستور /promise با یه فاصله وارد کنی.\nمثلاً:\n/promise 111"
    const val promiseRegisterInfo = "ثبت عهد جدید با ارسال شماره عهد پس از این دستور"
    const val alreadyHasPromise = "شما یک عهد قبلی دارید. جهت مشاهده عهد خود دستور زیر را وارد کنید.\n/info"
    const val wrongPromise = "عهد انتخابی معتبر نمی‌باشد."
    const val confirmPromiseData = "confirm-promise"
    const val confirmPromiseText = "بله، تأیید"
    const val rejectPromiseData = "reject-promise"
    const val rejectPromiseText = "خیر، تغییر"
    fun promiseConfirmDialog(promise: Promise, promiseId: Int, promiseType: PromiseType) = """
        |عهد انتخابی شما:
        |عهد شماره $promiseId
        |${promiseType.persianName}
        |${promise.content}
        |
        |عهد مورد تأیید است؟
    """.trimMargin()

    fun promiseInfoDialog(
        promise: Promise,
        userStatus: UserStatus,
        promiseType: PromiseType,
        decrementRemaining: Boolean = false
    ) = """
        |عهد انتخابی شما:
        |عهد شماره ${userStatus.promise}
        |${promiseType.persianName}
        |${promise.content}
        |روزهای باقی‌مانده تا پایان عهد: ${userStatus.remainingDays - if (decrementRemaining) 1 else 0}
    """.trimMargin()

    const val promiseReminder = "یادآوری عهد با امام زمان (عج)"

    const val promisesDBMapName = "promises"
    const val promiseConfirmed = "عهد با موفقیت ثبت شد."
    const val promiseRejected = "برای مشاهده لیست عهدها، یکی از دسته\u200Cها رو انتخاب کن."
    const val statusInfoInfo = "مشاهده عهد فعال"
    const val noPromiseFound = "شما عهد فعالی ندارید."
    const val removePromiseText = "حذف عهد"
    const val removePromiseData = "remove-promise"
    const val restartPromiseText = "بازنشانی عهد"
    const val restartPromiseData = "restart-promise"
    const val restartPromiseConfirm = "آیا از بازنشانی عهد اطمینان دارید؟"
    const val restartPromiseConfirmText = "بله"
    const val restartPromiseConfirmData = "restart-promise-confirm"
    const val restartPromiseRejectText = "خیر"
    const val restartPromiseRejectData = "restart-promise-reject"
    const val promiseRestarted = "عهد با موفقیت بازنشانی شد."
    const val removePromiseConfirm = "آیا از حذف عهد اطمینان دارید؟"
    const val removePromiseConfirmText = "بله"
    const val removePromiseConfirmData = "remove-promise-confirm"
    const val removePromiseRejectText = "خیر"
    const val removePromiseRejectData = "remove-promise-reject"
    const val promiseRemoved = "عهد با موفقیت حذف شد."
}