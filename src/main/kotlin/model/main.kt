package model

import kotlinx.serialization.Serializable
import java.util.*

enum class PromiseType(val id: Int, val persianName: String) {
    God(1, "در ارتباط با خدا"),
    People(2, "در ارتباط با مردم"),
    Self(3, "در ارتباط با خودم");

    companion object {
        fun getType(id: Int?) = when (id) {
            1 -> God
            2 -> People
            3 -> Self
            null -> null
            else -> throw IllegalArgumentException()
        }

        fun getTypeOrNull(id: Int?) = runCatching {
            getType(id)
        }.getOrNull()
    }
}

@Serializable
data class Promise(val id: Int, val content: String)

@Serializable
data class PromiseCategory(val promises: Array<Promise>, val audience: PromiseType) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PromiseCategory) return false

        if (!promises.contentEquals(other.promises)) return false
        if (audience != other.audience) return false

        return true
    }

    override fun hashCode(): Int {
        var result = promises.contentHashCode()
        result = 31 * result + audience.hashCode()
        return result
    }
}

data class UserStatus(val promise: Int, val remainingDays: Int = 40, private val registerTime: Date = Date()) :
    java.io.Serializable {
    val isReadyToSend: Boolean
        get() {
            val now = Date()
            val duration = now.time - registerTime.time
            return duration >= 24 * 60 * 60 * 1000
        }
}