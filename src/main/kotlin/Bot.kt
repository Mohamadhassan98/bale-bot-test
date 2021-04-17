import model.Promise
import model.PromiseType
import model.UserStatus
import org.telegram.abilitybots.api.bot.AbilityWebhookBot
import org.telegram.abilitybots.api.objects.*
import org.telegram.abilitybots.api.sender.SilentSender
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.io.File
import java.util.*

class Bot : AbilityWebhookBot(Constants.token, Constants.botUsername, Constants.botUsername) {

    override fun creatorId() = 714273093L

    override fun onRegister() {
        super.onRegister()
        silent.send("bot started", creatorId())
        kotlin.concurrent.fixedRateTimer(startAt = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
            set(Calendar.HOUR_OF_DAY, 10)
            if (!time.after(Date())) {
                add(Calendar.DATE, 1)
            }
        }.time, period = 24 * 60 * 60 * 1000) {
            val sentMessages = mutableSetOf<Long>()
            val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
            promises.entries.filter { (_, userStatus) ->
                userStatus.isReadyToSend
            }.forEach { (userId, userStatus) ->
                val exception = runCatching {
                    sender.execute(
                        SendMessage
                            .builder()
                            .chatId(userId.toString())
                            .parseMode(ParseMode.HTML)
                            .text(
                                "${Constants.promiseReminder}\n${
                                    Constants.promiseInfoDialog(
                                        getPromise(userStatus.promise)!!,
                                        userStatus,
                                        PromiseType.getType(userStatus.promise / 100)!!,
                                        decrementRemaining = true,
                                        includePayload = false
                                    )
                                }\n"
                            )
                            .build()
                    )
                    sender.execute(
                        SendPoll
                            .builder()
                            .isAnonymous(false)
                            .correctOptionId(0)
                            .question(
                                Constants.accomplished
                            )
                            .chatId(userId.toString())
                            .options(setOf("بله.", "خیر."))
                            .type("quiz")
                            .build()
                    )
                    if (userStatus.remainingDays == 1) {
                        sender.sendVideo(
                            SendVideo(
                                userId.toString(),
                                InputFile(File("./src/main/resources/video.mp4"))
                            )
                        )
                    }
                    sentMessages += userId
                }.exceptionOrNull() as? TelegramApiRequestException
                if (exception != null) {
                    if (exception.errorCode == 403 && exception.apiResponse == "Forbidden: bot was blocked by the user") {
                        promises.remove(userId)
                    }
                }
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

    //<editor-fold desc="poll answer">
    @Suppress("unused")
    val replyToPollAnswer: Reply
        get() = Reply.of({ _, it ->
            if (it.pollAnswer.optionIds[0] == 1) {
                silent.send("${Constants.sorry}${Constants.punishments.random()}", it.pollAnswer.user.id)
            }
        }, Flag.POLL_ANSWER)
    //</editor-fold>

    //<editor-fold desc="start">
    @Suppress("unused")
    val startBot: Ability
        get() = Ability
            .builder()
            .name("start")
            .info(Constants.startInfo)
            .action {
                silent.sendWithInlineKeyboard(Constants.welcome, it.chatId(), PromiseType.values().map { promiseType ->
                    promiseType.persianName to promiseType.name
                }.toMap())
                val starters = db.getSet<Long>(Constants.startersDBSetName)
                starters += it.chatId()
            }
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .build()
    //</editor-fold>

    //<editor-fold desc="promise types">
    @Suppress("unused")
    val replyToPromiseType: Reply
        get() = Reply.of({ _, it ->
            val promiseType = PromiseType.valueOf(it.callbackQuery.data)
            val data = "${Constants.selectedPromiseType} ${promiseType.persianName}:\n${
                promises.value.first {
                    it.audience == promiseType
                }.promises.joinToString("\n") {
                    "${promiseType.id}${it.id.toString().padStart(2, '0')}-${it.content}"
                }
            }\n${Constants.promiseRegisterHelp}"
            silent.send(data, it.callbackQuery.message.chatId)
        }, Flag.CALLBACK_QUERY, {
            runCatching { PromiseType.valueOf(it.callbackQuery.data) }.isSuccess
        })
    //</editor-fold>

    //<editor-fold desc="register promise">
    @Suppress("unused")
    val promise: Ability
        get() = Ability
            .builder()
            .name("promise")
            .info(Constants.promiseRegisterInfo)
            .privacy(Privacy.PUBLIC)
            .locality(Locality.USER)
            .input(0)
            .action {
                if (hasPromise(it.user().id)) {
                    silent.send(Constants.alreadyHasPromise, it.chatId())
                    return@action
                }
                val promiseId: String
                val remainingDay: String
                when (it.arguments().size) {
                    2 -> {
                        promiseId = it.arguments()[0]
                        remainingDay = it.arguments()[1]
                    }
                    1 -> {
                        promiseId = it.arguments()[0]
                        remainingDay = "40"
                    }
                    else -> {
                        silent.send(Constants.insufficientNumberOfArguments, it.chatId())
                        return@action
                    }
                }
                if (promiseId.toIntOrNull() == null || remainingDay.toIntOrNull() == null) {
                    silent.send(Constants.wrongPromise, it.chatId())
                    return@action
                }
                if (remainingDay.toInt() !in 5..100) {
                    silent.send(Constants.wrongPromise, it.chatId())
                    return@action
                }
                val promise = getPromise(promiseId.toInt())
                if (promise == null) {
                    silent.send(Constants.wrongPromise, it.chatId())
                    return@action
                }
                silent.sendWithInlineKeyboard(
                    Constants.promiseConfirmDialog(
                        promise,
                        promiseId.toInt(),
                        PromiseType.getType(promiseId.toInt() / 100)!!,
                    ), it.chatId(), mapOf(
                        Constants.confirmPromiseText to "${Constants.confirmPromiseData} $promiseId $remainingDay",
                        Constants.rejectPromiseText to "${Constants.rejectPromiseData} $promiseId $remainingDay"
                    ), ParseMode.HTML
                )
            }
            .build()
    //</editor-fold>

    //<editor-fold desc="statistics">
    @Suppress("unused")
    val statistics: Ability
        get() = Ability
            .builder()
            .name("statistics")
            .info(Constants.statisticsInfo)
            .locality(Locality.USER)
            .privacy(Privacy.ADMIN)
            .action {
                silent.sendWithInlineKeyboard(
                    Constants.chooseStatistics, it.chatId(), listOf(
                        mapOf(
                            Constants.startersText to Constants.startersData
                        ),
                        mapOf(
                            Constants.activeText to Constants.activeData
                        )
                    )
                )
            }
            .build()
    //</editor-fold>

    //<editor-fold desc="starter statistics">
    @Suppress("unused")
    val replyToStartersStatistics: Reply
        get() = Reply.of({ _, it ->
            val starters = db.getSet<Long>(Constants.startersDBSetName)
            silent.send("${Constants.starterStatistics}${starters.size}", it.callbackQuery.message.chatId)
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Constants.startersData
        })
    //</editor-fold>

    //<editor-fold desc="active statistics">
    @Suppress("unused")
    val replyToActiveStatistics: Reply
        get() = Reply.of({ _, it ->
            val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
            silent.send("${Constants.activeStatistics}${promises.size}", it.callbackQuery.message.chatId)
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Constants.activeData
        })
    //</editor-fold>

    //<editor-fold desc="promise choose">
    @Suppress("unused")
    val replyToPromiseChoose: Reply
        get() = Reply.of({ _, it ->
            if (hasPromise(it.callbackQuery.from.id)) {
                silent.send(Constants.alreadyHasPromise, it.callbackQuery.message.chatId)
                return@of
            }
            val data = it.callbackQuery.data
            val (result, promiseId, remaining) = data.split(" ")
            when (result) {
                Constants.confirmPromiseData -> {
                    val userPromises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
                    userPromises[it.callbackQuery.from.id] = UserStatus(promiseId.toInt(), remaining.toInt())
                    silent.send(Constants.promiseConfirmed, it.callbackQuery.message.chatId)
                }
                Constants.rejectPromiseData -> silent.sendWithInlineKeyboard(
                    Constants.promiseRejected,
                    it.callbackQuery.message.chatId,
                    PromiseType.values().map {
                        it.persianName to it.name
                    }.toMap()
                )
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data.startsWith(Constants.confirmPromiseData) || it.callbackQuery.data.startsWith(Constants.rejectPromiseData)
        })
    //</editor-fold>

    //<editor-fold desc="promise modify">
    @Suppress("unused")
    val replyToPromiseModify: Reply
        get() = Reply.of({ _, it ->
            if (!hasPromise(it.callbackQuery.from.id)) {
                silent.send(Constants.noPromiseFound, it.callbackQuery.message.chatId)
                return@of
            }
            when (it.callbackQuery.data) {
                Constants.restartPromiseData -> silent.sendWithInlineKeyboard(
                    Constants.restartPromiseConfirm, it.callbackQuery.message.chatId,
                    mapOf(
                        Constants.restartPromiseConfirmText to Constants.restartPromiseConfirmData,
                        Constants.restartPromiseRejectText to Constants.restartPromiseRejectData
                    )
                )
                Constants.removePromiseData -> silent.sendWithInlineKeyboard(
                    Constants.removePromiseConfirm,
                    it.callbackQuery.message.chatId,
                    mapOf(
                        Constants.removePromiseConfirmText to Constants.removePromiseConfirmData,
                        Constants.removePromiseRejectText to Constants.removePromiseRejectData
                    )
                )
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Constants.removePromiseData || it.callbackQuery.data == Constants.restartPromiseData
        })
    //</editor-fold>

    //<editor-fold desc="remove promise">
    @Suppress("unused")
    val replyToRemovePromise: Reply
        get() = Reply.of({ _, it ->
            if (!hasPromise(it.callbackQuery.from.id)) {
                silent.send(Constants.noPromiseFound, it.callbackQuery.message.chatId)
                return@of
            }
            when (it.callbackQuery.data) {
                Constants.removePromiseConfirmData -> {
                    val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
                    promises.remove(it.callbackQuery.from.id)
                    silent.send(Constants.promiseRemoved, it.callbackQuery.message.chatId)
                    silent.send(Constants.noPromiseFound, it.callbackQuery.message.chatId)
                }
                Constants.removePromiseRejectData -> {
                    val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
                    val userPromise = promises[it.callbackQuery.from.id]!!
                    silent.sendWithInlineKeyboard(
                        Constants.promiseInfoDialog(
                            getPromise(userPromise.promise)!!,
                            userPromise,
                            PromiseType.getType(userPromise.promise / 100)!!
                        ), it.callbackQuery.message.chatId, mapOf(
                            Constants.removePromiseText to Constants.removePromiseData,
                            Constants.restartPromiseText to Constants.restartPromiseData
                        ), ParseMode.HTML
                    )
                }
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Constants.removePromiseRejectData || it.callbackQuery.data == Constants.removePromiseConfirmData
        })
    //</editor-fold>

    //<editor-fold desc="restart promise">
    @Suppress("unused")
    val replyToRestartPromise: Reply
        get() = Reply.of({ _, it ->
            if (!hasPromise(it.callbackQuery.from.id)) {
                silent.send(Constants.noPromiseFound, it.callbackQuery.message.chatId)
                return@of
            }
            when (it.callbackQuery.data) {
                Constants.restartPromiseConfirmData -> {
                    val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
                    val userPromise = promises[it.callbackQuery.from.id]!!
                    promises[it.callbackQuery.from.id] = userPromise.copy(remainingDays = 40)
                    silent.send(Constants.promiseRestarted, it.callbackQuery.message.chatId)
                    silent.sendWithInlineKeyboard(
                        Constants.promiseInfoDialog(
                            getPromise(userPromise.promise)!!,
                            userPromise,
                            PromiseType.getType(userPromise.promise / 100)!!
                        ), it.callbackQuery.message.chatId, mapOf(
                            Constants.removePromiseText to Constants.removePromiseData,
                            Constants.restartPromiseText to Constants.restartPromiseData
                        ), ParseMode.HTML
                    )
                }
                Constants.restartPromiseRejectData -> {
                    val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
                    val userPromise = promises[it.callbackQuery.from.id]!!
                    silent.sendWithInlineKeyboard(
                        Constants.promiseInfoDialog(
                            getPromise(userPromise.promise)!!,
                            userPromise,
                            PromiseType.getType(userPromise.promise / 100)!!
                        ), it.callbackQuery.message.chatId, mapOf(
                            Constants.removePromiseText to Constants.removePromiseData,
                            Constants.restartPromiseText to Constants.restartPromiseData
                        ), ParseMode.HTML
                    )
                }
            }
        }, Flag.CALLBACK_QUERY, {
            it.callbackQuery.data == Constants.restartPromiseRejectData || it.callbackQuery.data == Constants.restartPromiseConfirmData
        })
    //</editor-fold>

    //<editor-fold desc="info">
    @Suppress("unused")
    val statusInfo: Ability
        get() = Ability
            .builder()
            .name("info")
            .info(Constants.statusInfoInfo)
            .locality(Locality.USER)
            .privacy(Privacy.PUBLIC)
            .action {
                val promises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
                val userPromise = promises[it.user().id]
                if (userPromise == null) {
                    silent.send(Constants.noPromiseFound, it.chatId())
                } else {
                    silent.sendWithInlineKeyboard(
                        Constants.promiseInfoDialog(
                            getPromise(userPromise.promise)!!,
                            userPromise,
                            PromiseType.getType(userPromise.promise / 100)!!
                        ), it.chatId(), mapOf(
                            Constants.removePromiseText to Constants.removePromiseData,
                            Constants.restartPromiseText to Constants.restartPromiseData
                        ), ParseMode.HTML
                    )
                }
            }
            .build()
    //</editor-fold>

    private fun SilentSender.sendWithInlineKeyboard(
        textMessage: String,
        id: Long,
        rows: List<Map<String, String>>,
        parseMode: String? = null
    ) = execute(SendMessage().apply {
        text = textMessage
        chatId = id.toString()
        if (parseMode != null) {
            this.parseMode = parseMode
        }
        replyMarkup = InlineKeyboardMarkup
            .builder()
            .keyboard(
                rows.map {
                    it.map { (text, data) ->
                        InlineKeyboardButton
                            .builder()
                            .text(text)
                            .callbackData(data)
                            .build()
                    }
                }
            )
            .build()
    })

    private fun SilentSender.sendWithInlineKeyboard(
        textMessage: String,
        id: Long,
        buttons: Map<String, String>,
        parseMode: String? = null
    ) = sendWithInlineKeyboard(textMessage, id, listOf(buttons), parseMode)

    private fun hasPromise(userId: Long, promiseId: Int? = null): Boolean {
        val userPromises = db.getMap<Long, UserStatus>(Constants.promisesDBMapName)
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

object Constants {
    const val token = "1785034611:AAHwESqrCfLGeYXg84CwJwvOJClOB5rIpmk"
    const val botUsername = "Zohour_underlies_bot"
    const val botServerPath = "https://bot.safirict.com"
    const val serverPort = 8000
    val welcome =
        """|به بات عهد با امام زمان خوش اومدی.
           |توی این بات ما چند تا عهد رو بهت معرفی می‌کنیم تا به دلخواه خودت یکی رو انتخاب کنی. بعد از اون ما تا چهل روز بهت یادآوری می‌کنیم که عهدت با امام زمانت رو فراموش نکنی.
           |عهدهایی که می‌تونی با امام زمان (عج) ببندی می‌تونه در رابطه با سه دسته افراد باشه که برای این که راحت‌تر بتونی عهد مورد نظرت رو پیدا کنی ما هم با همین دسته‌بندی عهدها رو بهت نشون می‌دیم. این سه دسته عبارتند از:
           |۱- در رابطه با خدا و اهل بیت (ع)
           |۲- در رابطه با مردم
           |۳- در رابطه با خودت
           |برای مشاهده لیست عهدها، یکی از دسته‌ها رو انتخاب کن.
""".trimMargin()
    const val startInfo = "شروع کار با بات"
    const val selectedPromiseType = "نمایش لیست عهدها"
    const val promiseRegisterHelp =
        "برای انتخاب هر یک از عهدها کافیه شماره عهد رو بعد از دستور /promise با یه فاصله وارد کنی.\nمثلاً:\n/promise 111\nهمچنین می‌تونی بعد از شماره عهد، روزهایی که می‌خوای بهت یادآوری بشه رو وارد کنی. مثلاً:\n/promise 111 20"
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
        |${if (promise.payload == null) "" else "اطلاعات بیشتر: \n${promise.payload}"}
        |عهد مورد تأیید است؟
    """.trimMargin()

    const val insufficientNumberOfArguments = "تعداد پارامترهای ورودی مناسب نیست."

    fun promiseInfoDialog(
        promise: Promise,
        userStatus: UserStatus,
        promiseType: PromiseType,
        decrementRemaining: Boolean = false,
        includePayload: Boolean = true
    ) = """
        |عهد انتخابی شما:
        |عهد شماره ${userStatus.promise}
        |${promiseType.persianName}
        |${promise.content}
        |${if (promise.payload == null || !includePayload) "" else "اطلاعات بیشتر: \n${promise.payload}"}
        |روزهای باقی‌مانده تا پایان عهد: ${userStatus.remainingDays - if (decrementRemaining) 1 else 0}
    """.trimMargin()

    const val promiseReminder = "یادآوری عهد با امام زمان (عج)"
    const val accomplished = "امروز به عهدت وفا کردی؟"

    const val promisesDBMapName = "promises"
    const val startersDBSetName = "starters"
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
    const val sorry = "متأسفم. به خاطر این عهدشکنی جریمه میشی!\nجریمه امروزت:\n"
    val punishments = arrayOf(
        "عدم استفاده از فضای مجازی به مدت دو روز",
        "محرومیت از تماشای فیلم و برنامه\u200Cهای مورد علاقه تلویزیونی به مدت سه روز",
        "حذف یک وعده غذایی از برنامه غذایی",
        "نظافت کل محیط خانه به تنهایی",
        "انجام کارهای شخصی دیگر اعضای خانواده",
        "یک ساعت پیاده\u200Cروی و فکر کردن به تخلف از عهد",
        "نظافت سرویس بهداشتی منزل",
        "پهن و جمع کردن سفره و وسایل آن به مدت سه روز"
    )
    const val statisticsInfo = "آمار استفاده از بات را نشان می دهد."
    const val chooseStatistics = "نوع آمار مورد نظر خود را انتخاب کنید."
    const val startersText = "افرادی که بات را آغاز کرده اند"
    const val startersData = "starters"
    const val activeText = "افرادی که عهد فعال دارند"
    const val activeData = "active"
    const val starterStatistics = "تعداد افرادی که بات را آغاز کرده اند: "
    const val activeStatistics = "تعداد افرادی که هم اکنون عهد فعال دارند: "
}