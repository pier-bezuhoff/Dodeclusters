package domain.io

typealias DdcContent = String
typealias UserId = String
typealias SharedId = String

data class SharedLink(
    val sharedId: SharedId,
    val owned: Boolean = false,
)

interface DdcSharing {
    /** @return (ddc content, is shared link owned by the user) */
    suspend fun fetchSharedDdc(sharedId: SharedId): Pair<DdcContent, Boolean>?
    suspend fun registerSharer(): UserId?
    fun testSharePermission(): Boolean
    suspend fun shareNewDdc(content: DdcContent): SharedId?
    suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): SharedId?
}