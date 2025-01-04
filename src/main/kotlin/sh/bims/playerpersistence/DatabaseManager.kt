package sh.bims.playerpersistence

import org.jetbrains.exposed.sql.Database
import org.postgresql.util.PSQLException
import java.sql.DriverManager

object DatabaseManager {
    private lateinit var databaseInstance: Database
    private lateinit var serverNodeValue: String

    val database: Database
        get() = databaseInstance

    val SERVER_NODE: String
        get() = serverNodeValue

    fun initialize(url: String, user: String, password: String, serverNode: String) {
        val dbName = extractDatabaseName(url)
        var baseUrl = extractBaseUrl(url)

        PlayerPersistence.logger.info("Initializing DatabaseManager for server node: $serverNode")

        if (!baseUrl.endsWith("/"))
            baseUrl += "/"
        
        ensureDatabaseExists(baseUrl, dbName, user, password)
        
        databaseInstance = Database.connect(
            url = url,
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
        serverNodeValue = serverNode
        PlayerPersistence.logger.info("Database connection established successfully for server node: $serverNode")
    }

    private fun ensureDatabaseExists(baseUrl: String, dbName: String, user: String, password: String) {
        PlayerPersistence.logger.info("Checking if database '$dbName' exists...")

        try {
            DriverManager.getConnection(baseUrl, user, password).use { connection ->
                val statement = connection.createStatement()

                statement.execute("CREATE DATABASE IF NOT EXISTS $dbName")
                PlayerPersistence.logger.info("Database '$dbName' created successfully.")
            }
        } catch (error: PSQLException) {
            PlayerPersistence.logger.error("Failed to ensure database exists: ${error.message}", error)
            throw error
        }
    }

    private fun extractDatabaseName(url: String): String {
        return url.substringAfterLast("/")
    }

    private fun extractBaseUrl(url: String): String {
        return url.substringBeforeLast("/")
    }
}