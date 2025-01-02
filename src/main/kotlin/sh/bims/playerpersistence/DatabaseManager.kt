package sh.bims.playerpersistence

import org.jetbrains.exposed.sql.Database

object DatabaseManager {
    private lateinit var databaseInstance: Database
    private lateinit var serverNodeValue: String

    val database: Database
        get() = databaseInstance

    val SERVER_NODE: String
        get() = serverNodeValue

    fun initialize(url: String, user: String, password: String, serverNode: String) {
        databaseInstance = Database.connect(
            url = url,
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
        
        serverNodeValue = serverNode
    }
}