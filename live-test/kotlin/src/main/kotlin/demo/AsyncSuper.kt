package demo

interface AsyncFetcher {
    suspend fun fetch(): String
}

class AsyncImpl : AsyncFetcher {
    override suspend fun fetch(): String = "fetched"
}
