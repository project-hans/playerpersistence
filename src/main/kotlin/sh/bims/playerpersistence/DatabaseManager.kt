package sh.bims.playerpersistence

import org.jetbrains.exposed.sql.Database
object DatabaseManager {
    private lateinit var databaseInstance: Database
    private lateinit var serverNodeValue: String

    val database: Database
        get() = databaseInstance

    fun initialize(url: String, user: String, password: String, serverNode: String) {
        PlayerPersistence.logger.info("Initializing DatabaseManager for server node: $serverNode")
        databaseInstance = Database.connect(
            url = url,
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
        serverNodeValue = serverNode
        PlayerPersistence.logger.info("Database connection established successfully for server node: $serverNode")
    }
}