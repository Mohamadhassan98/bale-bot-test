import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.PromiseCategory
import java.io.File

val promises = lazy {
    val file = File("./src/main/resources/promises.json")
    Json.decodeFromString<Array<PromiseCategory>>(file.readText())
}