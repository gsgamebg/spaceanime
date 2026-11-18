package app.anim.update

interface RequiredReleaseStore {
    suspend fun load(): AppRelease?
    suspend fun save(release: AppRelease)
    suspend fun clear()
}
